package com.kvaster.gsuite;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.Base64;
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.model.Alias;
import com.google.api.services.admin.directory.model.Aliases;
import com.google.api.services.admin.directory.model.User;
import com.google.api.services.admin.directory.model.UserExternalId;
import com.google.api.services.admin.directory.model.UserName;
import com.google.api.services.admin.directory.model.UserPhone;
import com.google.api.services.admin.directory.model.Users;
import com.google.common.io.BaseEncoding;
import com.kvaster.gsuite.GSuiteSyncConfig.Domain;
import com.kvaster.gsuite.GSuiteSyncConfig.LdapConfig;
import com.kvaster.gsuite.GSuiteSyncConfig.MailConfig;
import com.kvaster.utils.email.CommonsEmailFactory;
import com.kvaster.utils.email.Email;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.AsyncRequestID;
import com.unboundid.ldap.sdk.AsyncSearchResultListener;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchResultReference;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.controls.ContentSyncRequestControl;
import com.unboundid.ldap.sdk.controls.ContentSyncRequestMode;
import com.unboundid.ldap.sdk.controls.ContentSyncStateControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSuiteSyncService {
    private static final Logger LOG = LoggerFactory.getLogger(GSuiteSyncService.class);

    // we need to add some version to 'last update' field in order to be able
    // to do full resync in case of significant code changess
    private static final String SYNC_VERSION = "1";

    private static final String SHA_PREFIX = "{SHA}";

    private final Directory directory;
    private final LdapHelper ldapHelper;

    private final List<Domain> domains;

    private final LdapConfig ldapConfig;

    private final CommonsEmailFactory emailFactory;
    private final String emailFrom;
    private final List<String> emailTo;

    private final ScheduledThreadPoolExecutor scheduler;

    private final long retrySyncInMillis;

    // TODO We're using only one thread for tasks, but we should check if it's really thread safe
    private volatile LDAPConnection listenConnection;
    private volatile AsyncRequestID listenReqId;
    private volatile ASN1OctetString cookie;

    private enum Status {
        IDLE,
        SCHEDULED,
        SYNCING,
        SYNCING_REPEAT
    }

    private Status syncStatus = Status.IDLE;
    // some delay on startup - to be sure all initial changes will be received
    private long lastSync = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);

    public GSuiteSyncService(GSuiteSyncConfig config) throws Exception {
        GSuiteSyncConfig.GSuiteConfig gc = config.getGsuiteConfig();
        domains = gc.getDomains();

        directory = GoogleHelper.createDirectoryService(gc.getCredentialsFile(), gc.getDelegatedUser());

        retrySyncInMillis = TimeUnit.SECONDS.toMillis(gc.getSyncRetryDelaySeconds());

        ldapConfig = config.getLdapConfig();
        ldapHelper = new LdapHelper(ldapConfig.getUrl());

        MailConfig mailConfig = config.getMailConfig();
        emailFactory = new CommonsEmailFactory(
                mailConfig.getHost(), mailConfig.getLogin(),
                mailConfig.getPassword(), mailConfig.getAddress(),
                mailConfig.getThreads(), mailConfig.getSendRetries(),
                mailConfig.getRetryDelayMillis()
        );

        emailFrom = mailConfig.getFrom();
        emailTo = mailConfig.getTo();

        scheduler = new ScheduledThreadPoolExecutor(1);
    }

    public void startService() {
        scheduler.schedule(this::setupLdapListener, 0, TimeUnit.MILLISECONDS);
    }

    public void stopService() {
        LOG.info("Stopping...");

        scheduler.shutdownNow();

        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                LOG.error("Timeout while waitig tasks to stop");
            }
        } catch (InterruptedException ie) {
            LOG.error("Interrupted while stopping", ie);
        }

        closeSearch();

        emailFactory.stop();

        LOG.info("Stopped.");
    }

    private void scheduleSync(long delay) {
        synchronized (this) {
            switch (syncStatus) {
                case IDLE:
                    LOG.info("Change detected, sync scheduled.");
                    long now = System.currentTimeMillis();
                    long syncTime = Math.max(now + delay, lastSync + TimeUnit.SECONDS.toMillis(3));
                    syncStatus = Status.SCHEDULED;
                    scheduler.schedule(this::doSyncIfNeed, syncTime - now, TimeUnit.MILLISECONDS);
                    break;

                case SYNCING:
                    syncStatus = Status.SYNCING_REPEAT;
                    break;
            }
        }
    }

    private void forceScheduleSync(long delay) {
        syncStatus = Status.IDLE;
        scheduleSync(delay);
    }

    private void doSyncIfNeed() {
        synchronized (this) {
            syncStatus = Status.SYNCING;
        }

        boolean isOk = doSyncSafe();

        synchronized (this) {
            lastSync = System.currentTimeMillis();

            if (isOk) {
                if (syncStatus == Status.SYNCING) {
                    syncStatus = Status.IDLE;
                } else {
                    forceScheduleSync(0);
                }
            } else {
                forceScheduleSync(retrySyncInMillis);
            }
        }
    }

    private boolean doSyncSafe() {
        LOG.info("Syncing ldap with gsuite");

        Msgs msgs = new Msgs();

        boolean isOk = true;

        try {
            doSync(msgs);
        } catch (GoogleJsonResponseException ge) {
            LOG.error("Google error", ge);
            msgs.error("google error: %s", ge.getDetails().getMessage());

            isOk = false;
        } catch (Exception e) {
            LOG.error("General error", e);
            msgs.error("general error: %s", e.toString());

            isOk = false;
        }

        try {
            if (msgs.hasMessages()) {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                Email m = emailFactory.createEmail();
                m.setFrom(emailFrom);
                emailTo.forEach(m::addTo);
                m.setSubject("[" + msgs.getSubject() + "] sync completed at " + format.format(new Date()));
                m.setMessage(msgs.getMessages());
                m.send();
            }
        } catch (Exception e) {
            LOG.error("Fatal error sending report", e);
        }

        LOG.info("Sync finished");

        return isOk;
    }

    private void doSync(Msgs msgs) throws LDAPException, IOException {
        Map<String, LdapUser> ldapUsers = getLdapUsers(msgs).stream()
                .collect(Collectors.toMap((u) -> u.login, (u) -> u));

        Map<String, User> gsuiteUsers = getGSuiteUsers().stream()
                .collect(Collectors.toMap(User::getPrimaryEmail, (u) -> u));

        Set<String> forDel = new TreeSet<>();
        Set<String> forAdd = new TreeSet<>();
        Set<String> forUpd = new TreeSet<>();

        ldapUsers.values().forEach((u) -> {
            if (u.needSync && u.password != null) {
                String login = u.login;
                User gu = gsuiteUsers.get(login);
                if (gu == null) {
                    forAdd.add(login);
                } else if (needSync(u, gu)) {
                    forUpd.add(login);
                }
            }
        });

        gsuiteUsers.values().forEach((u) -> {
            String login = u.getPrimaryEmail();
            LdapUser lu = ldapUsers.get(login);
            if (lu == null) {
                forDel.add(login);
            }
        });

        LOG.info("for del: {}, add: {}, update: {}", forDel.size(), forAdd.size(), forUpd.size());

        List<LdapUser> forAliasUpdate = new ArrayList<>();

        forDel.forEach((login) -> {
            LOG.info("User should be deleted manually: {}", login);
            msgs.warn("user should be deleted manually: %s", login);
        });

        forAdd.forEach((login) -> {
            LdapUser lu = ldapUsers.get(login);

            LOG.info("Adding user: {}", login);

            User user = createUser(lu, msgs);
            if (user == null) {
                msgs.warn("can't add user: %s", login);
                return;
            }

            try {
                directory.users().insert(user).execute();

                if (lu.aliases.size() > 0) {
                    forAliasUpdate.add(lu);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error adding new user", e);
            }

            msgs.info("user added: %s", login);
        });

        forUpd.forEach((login) -> {
            LdapUser lu = ldapUsers.get(login);

            LOG.info("Updating user: {}", login);

            User user = createUser(lu, msgs);
            if (user == null) {
                msgs.warn("can't update user: %s", login);
                return;
            }

            try {
                directory.users().update(login, user).execute();
            } catch (IOException e) {
                throw new RuntimeException("Error updating user", e);
            }

            if (needAliasUpdate(lu, gsuiteUsers.get(login))) {
                forAliasUpdate.add(lu);
            }

            msgs.info("user updated: %s", login);
        });

        forAliasUpdate.forEach((lu) -> {
            try {
                Aliases aliases = directory.users().aliases().list(lu.login).execute();
                Set<String> all = new HashSet<>();
                if (aliases.getAliases() != null) {
                    aliases.getAliases().forEach((o) -> {
                        @SuppressWarnings("unchecked")
                        Map<String, String> a = (Map<String, String>) o;
                        if ("admin#directory#alias".equals(a.get("kind"))) {
                            all.add(a.get("alias"));
                        }
                    });
                }

                lu.aliases.forEach((a) -> {
                    if (!all.contains(a)) {
                        LOG.info("Adding alias {} for {}", a, lu.login);

                        try {
                            directory.users().aliases().insert(lu.login, new Alias().setAlias(a)).execute();
                        } catch (IOException e) {
                            throw new RuntimeException("Error adding alias", e);
                        }

                        msgs.info("alias added: %s for %s", a, lu.login);
                    }
                });

                all.forEach((a) -> {
                    if (!lu.aliases.contains(a)) {
                        LOG.info("Deleting alias {} for {}", a, lu.login);

                        try {
                            directory.users().aliases().delete(lu.login, a).execute();
                        } catch (IOException e) {
                            throw new RuntimeException("Error deleting alias", e);
                        }

                        msgs.info("alias deleted: %s for %s", a, lu.login);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Error updating aliases", e);
            }
        });
    }

    private String getLastModify(User gu) {
        if (gu.getExternalIds() != null) {
            // Latest google client library does not parse this object as UserExternalID
            //noinspection unchecked
            return ((List<Map<String, String>>) gu.getExternalIds())
                    .stream()
                    .filter((e) -> "custom".equals(e.get("type")) && "lastModify".equals(e.get("customType")))
                    .map((e) -> e.get("value"))
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private boolean needAliasUpdate(LdapUser lu, User gu) {
        if (gu.getAliases() == null) {
            return lu.aliases.size() != 0;
        }

        return !lu.aliases.equals(new HashSet<>(gu.getAliases()));
    }

    private boolean needSync(LdapUser lu, User gu) {
        if (!Objects.equals(lu.lastModify, getLastModify(gu))) {
            return true;
        }

        UserName name = gu.getName();
        if (!Objects.equals(lu.givenName, name.getGivenName())
                || !Objects.equals(lu.surName, name.getFamilyName())
                || !Objects.equals(lu.givenName + ' ' + lu.surName, name.getFullName())) {
            return true;
        }

        if (needAliasUpdate(lu, gu)) {
            return true;
        }

        if (!Objects.equals(lu.orgUnit, gu.getOrgUnitPath())) {
            return true;
        }

        if (lu.searchable != gu.getIncludeInGlobalAddressList()) {
            return true;
        }

        // Latest google client does not parse this value as UserPhone :(
        @SuppressWarnings("unchecked")
        List<Map<String, String>> phones = (List<Map<String, String>>) gu.getPhones();
        if (lu.phone == null) {
            if (phones != null && phones.size() != 0) {
                return true;
            }
        } else {
            String phone = null;
            if (phones != null && phones.size() == 1) {
                Map<String, String> p = phones.get(0);
                if ("mobile".equals(p.get("type"))) {
                    phone = phones.get(0).get("value");
                }
            }

            if (!lu.phone.equals(phone)) {
                return true;
            }
        }

        return false;
    }

    private User createUser(LdapUser lu, Msgs msgs) {
        User user = new User();
        user.setPrimaryEmail(lu.login);
        user.setChangePasswordAtNextLogin(false);
        user.setOrgUnitPath(lu.orgUnit);
        user.setIncludeInGlobalAddressList(lu.searchable);

        if (lu.password != null && lu.password.startsWith(SHA_PREFIX)) {
            user.setHashFunction("SHA-1");
            user.setPassword(BaseEncoding.base16()
                    .encode(Base64.decodeBase64(lu.password.substring(SHA_PREFIX.length()))));
        } else {
            LOG.warn("User has no SHA password: {}", lu.login);
            msgs.warn("user has no SHA password: %s", lu.login);
            return null;
        }

        user.setAliases(new ArrayList<>(lu.aliases));

        UserName name = new UserName();
        name.setGivenName(lu.givenName);
        name.setFamilyName(lu.surName);
        user.setName(name);

        UserExternalId eid = new UserExternalId();
        eid.setType("custom");
        eid.setCustomType("lastModify");
        eid.setValue(lu.lastModify);
        List<UserExternalId> eids = new ArrayList<>();
        eids.add(eid);
        user.setExternalIds(eids);

        if (lu.phone != null) {
            UserPhone phone = new UserPhone();
            phone.setValue(lu.phone);
            phone.setType("mobile");
            List<UserPhone> phones = new ArrayList<>();
            phones.add(phone);
            user.setPhones(phones);
        }

        return user;
    }

    /////// Google ///////

    private List<User> getGSuiteUsers() throws IOException {
        List<User> allUsers = new ArrayList<>();
        String nextPage = null;

        while (true) {
            Users result = directory.users().list()
                    .setCustomer("my_customer")
                    .setPageToken(nextPage)
                    .execute();

            List<User> users = result.getUsers();
            if (users == null) {
                break;
            }

            users.forEach((u) -> {
                String login = u.getPrimaryEmail();

                // Looking only for controlled domains
                if (domains.stream().anyMatch((d) -> isInDomain(login, d))) {
                    allUsers.add(u);
                }
            });

            nextPage = result.getNextPageToken();
            if (nextPage == null) {
                break;
            }
        }

        return allUsers;
    }

    /////// LDAP ///////

    private static class LdapUser {
        String givenName;
        String surName;
        String login;
        Set<String> aliases;
        String password;
        String phone;
        String orgUnit;
        boolean searchable;
        String lastModify;
        boolean needSync;

        /**
         * Use data from LDAP
         *
         * @param givenName  - user given name
         * @param surName    - user family name
         * @param login      - login - main email
         * @param aliases    - email aliases (only for controlled domains)
         * @param password   - password (it shoul be only SHA-1 hash in order to be able to sync with google)
         * @param phone      - phone number
         * @param orgUnit    - organization unit in gsuite
         * @param searchable - should we be able to search for contact ?
         * @param lastModify - last modify time from ldap
         * @param needSync   - should we sync this user with gsuite
         */
        LdapUser(
                String givenName, String surName, String login, Set<String> aliases,
                String password, String phone, String orgUnit, boolean searchable,
                String lastModify, boolean needSync
        ) {
            this.givenName = givenName;
            this.surName = surName;
            this.login = login;
            this.aliases = aliases;
            this.password = password;
            this.phone = phone;
            this.orgUnit = orgUnit;
            this.searchable = searchable;
            this.lastModify = lastModify;
            this.needSync = needSync;
        }
    }

    private static <T> T orDefault(T value, T defValue) {
        return value == null ? defValue : value;
    }

    private static boolean isInDomain(String mail, Domain domain) {
        return isInDomain(mail, domain.getDomain());
    }

    private static boolean isInDomain(String mail, String domain) {
        return mail.endsWith('@' + domain);
    }

    private LdapUser getLdapUser(SearchResultEntry e, Msgs msgs) {
        String employeeType = orDefault(e.getAttributeValue("employeeType"), "");

        switch (employeeType) {
            case "":
            case "hidden":
                // normal user - processing
                break;

            case "failed":
                // this user is failed - skipping
                return null;

            case "service":
                // this user is service and it is used for sending through our own smtp - skipping
                return null;

            default:
                // unknown user type
                LOG.warn("Unknown employee type: {}, for dn: {}", employeeType, e.getDN());
                return null;
        }

        String uid = e.getAttributeValue("uid");

        String login = null;
        String orgUnit = null;
        int priority = Integer.MAX_VALUE;
        Set<String> mails = new HashSet<>();

        Attribute attr = e.getAttribute("mail");
        if (attr != null) {
            for (String mail : attr.getValues()) {
                boolean accepted = false;

                int p = 0;

                for (Domain d : domains) {
                    String targetMail = uid;
                    if (!targetMail.contains("@")) {
                        targetMail = targetMail + '@' + d.getDomain();
                    }

                    if (isInDomain(mail, d) && isInDomain(targetMail, d)) {
                        accepted = true;

                        if (mail.equals(targetMail)) {
                            if (priority > p) {
                                priority = p;
                                login = mail;
                                orgUnit = d.getOrgUnit();
                            }
                        }
                    }

                    if (accepted) {
                        mails.add(mail);
                    }

                    p++;
                }
            }
        }

        if (login == null) {
            if (mails.size() > 0) {
                LOG.warn("User should be controlled by domains, but it is not: {}", e.getDN());
                msgs.warn("user should be controlled, but it is not: %s", e.getDN());
            }

            return null;
        }

        mails.remove(login);

        String surname = e.getAttributeValue("sn");
        String name = e.getAttributeValue("givenName");
        String password = e.getAttributeValue("userPassword");
        String phone = e.getAttributeValue("mobile");
        String lastModify = e.getAttributeValue("modifyTimestamp") + '-' + SYNC_VERSION;

        boolean searchable = !"hidden".equals(employeeType);

        return new LdapUser(
                name, surname, login, mails, password,
                phone, orgUnit, searchable, lastModify,
                true
        );
    }

    private LDAPConnection connect() throws LDAPException {
        return ldapHelper.connect(ldapConfig.getBindDn(), ldapConfig.getBindPw());
    }

    private List<LdapUser> getLdapUsers(Msgs msgs) throws LDAPException {
        try (LDAPConnection connection = connect()) {
            List<LdapUser> users = new ArrayList<>();

            SearchResult sr = connection.search(ldapConfig.getLoginDn(),
                    SearchScope.SUB,
                    Filter.createEqualityFilter("objectClass", "inetOrgPerson"),
                    "*",
                    SearchRequest.ALL_OPERATIONAL_ATTRIBUTES);

            LOG.debug("Found {} ldap entries", sr.getEntryCount());

            sr.getSearchEntries().forEach((e) -> {
                LdapUser u = getLdapUser(e, msgs);
                if (u != null) {
                    users.add(u);
                }
            });

            return users;
        }
    }

    private void closeSearch() {
        closeSearch(listenConnection, listenReqId);

        listenConnection = null;
        listenReqId = null;
    }

    private void closeSearch(LDAPConnection connection, AsyncRequestID reqId) {
        if (connection != null) {
            if (reqId != null) {
                try {
                    connection.abandon(reqId);
                } catch (LDAPException e) {
                    LOG.warn("Error on canceling search request", e);
                }

                connection.close();
            }
        }
    }

    private void scheduleSetupLdapListener() {
        scheduler.schedule(this::setupLdapListener, ldapConfig.getReconnectDelayMillis(), TimeUnit.MILLISECONDS);
    }

    private void updateCookie(ContentSyncStateControl control) {
        if (control != null) {
            ASN1OctetString c = control.getCookie();

            if (c != null) {
                cookie = c;
                LOG.debug("new cookie: {}", c.stringValue());
            }
        }
    }

    private void setupLdapListener() {
        LOG.info("Connecting to ldap");

        LDAPConnection connection = null;
        AsyncRequestID reqId = null;

        try {
            connection = connect();

            AsyncSearchResultListener listener = new AsyncSearchResultListener() {
                @Override
                public void searchResultReceived(AsyncRequestID requestID, SearchResult searchResult) {
                    LOG.info("Search result received -> connection closed");

                    updateCookie((ContentSyncStateControl) searchResult.getResponseControl(ContentSyncStateControl.SYNC_STATE_OID));

                    closeSearch();
                    scheduleSetupLdapListener();
                }

                @Override
                public void searchEntryReturned(SearchResultEntry searchEntry) {
                    // LOG.info("Ldap entry change occurred");

                    updateCookie((ContentSyncStateControl) searchEntry.getControl(ContentSyncStateControl.SYNC_STATE_OID));

                    scheduleSync(0);
                }

                @Override
                public void searchReferenceReturned(SearchResultReference searchReference) {
                    // do nothing
                }
            };

            SearchRequest req = new SearchRequest(listener,
                    ldapConfig.getLoginDn(),
                    SearchScope.SUB,
                    Filter.createEqualityFilter("objectClass", "inetOrgPerson"),
                    "*",
                    SearchRequest.ALL_OPERATIONAL_ATTRIBUTES);

            req.addControl(new ContentSyncRequestControl(
                    ContentSyncRequestMode.REFRESH_AND_PERSIST,
                    cookie,
                    false
            ));

            reqId = connection.asyncSearch(req);

            listenConnection = connection;
            listenReqId = reqId;

            LOG.info("Connect to ldap succeeded");
        } catch (Exception e) {
            LOG.error("Error connecting to LDAP", e);
            closeSearch(connection, reqId);

            scheduleSetupLdapListener();
        }
    }
}
