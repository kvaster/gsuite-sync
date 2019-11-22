package com.kvaster.gsuite;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

class GSuiteSyncConfig {
    public static class Domain {
        private String domain;
        private String orgUnit;

        protected Domain() {
            // for deserialization
        }

        public Domain(String domain, String orgUnit) {
            this.domain = domain;
            this.orgUnit = orgUnit;
        }

        public String getDomain() {
            return domain;
        }

        public String getOrgUnit() {
            return orgUnit;
        }
    }

    public static class GSuiteConfig {
        private List<Domain> domains;
        private File credentialsFile;
        private String delegatedUser;
        private boolean reportUncontrolled = true;

        private int syncRetryDelaySeconds = 600;

        protected GSuiteConfig() {
            // for deserialization
        }

        public GSuiteConfig(
                List<Domain> domains,
                File credentialsFile,
                String delegatedUser,
                boolean reportUncontrolled,
                int syncRetryDelaySeconds
        ) {
            this.domains = domains;
            this.credentialsFile = credentialsFile;
            this.delegatedUser = delegatedUser;
            this.reportUncontrolled = reportUncontrolled;
            this.syncRetryDelaySeconds = syncRetryDelaySeconds;
        }

        public List<Domain> getDomains() {
            return domains;
        }

        public File getCredentialsFile() {
            return credentialsFile;
        }

        public String getDelegatedUser() {
            return delegatedUser;
        }

        public boolean getReportUncontrolled() {
            return reportUncontrolled;
        }

        public int getSyncRetryDelaySeconds() {
            return syncRetryDelaySeconds;
        }
    }

    public static class LdapConfig {
        private String url;
        private String loginDn;
        private String loginFilter;
        private String groupDn;
        private String groupFilter;
        private String bindDn;
        private String bindPw;
        private long reconnectDelayMillis = TimeUnit.SECONDS.toMillis(5);

        protected LdapConfig() {
            // for deserialization
        }

        public LdapConfig(
                String url, String loginDn, String loginFilter, String groupDn,
                String groupFilter, String bindDn, String bindPw, long reconnectDelayMillis
        ) {
            this.url = url;
            this.loginDn = loginDn;
            this.loginFilter = loginFilter;
            this.groupDn = groupDn;
            this.groupFilter = groupFilter;
            this.bindDn = bindDn;
            this.bindPw = bindPw;
            this.reconnectDelayMillis = reconnectDelayMillis;
        }

        public String getUrl() {
            return url;
        }

        public String getLoginDn() {
            return loginDn;
        }

        public String getLoginFilter() {
            return loginFilter;
        }

        public String getGroupDn() {
            return groupDn;
        }

        public String getGroupFilter() {
            return groupFilter;
        }

        public String getBindDn() {
            return bindDn;
        }

        public String getBindPw() {
            return bindPw;
        }

        public long getReconnectDelayMillis() {
            return reconnectDelayMillis;
        }
    }

    public static class MailConfig {
        private String host;
        private String login;
        private String password;
        private String address;
        private int threads = 1;
        private int sendRetries = 3;
        private long retryDelayMillis = 1000;

        private String from;
        private List<String> to;

        protected MailConfig() {
            // for deserialization
        }

        public MailConfig(
                String host, String login, String password, String address, int threads, int sendRetries,
                long retryDelayMillis, String from, List<String> to
        ) {
            this.host = host;
            this.login = login;
            this.password = password;
            this.address = address;
            this.threads = threads;
            this.sendRetries = sendRetries;
            this.retryDelayMillis = retryDelayMillis;
            this.from = from;
            this.to = to;
        }

        public String getHost() {
            return host;
        }

        public String getLogin() {
            return login;
        }

        public String getPassword() {
            return password;
        }

        public String getAddress() {
            return address;
        }

        public int getThreads() {
            return threads;
        }

        public int getSendRetries() {
            return sendRetries;
        }

        public long getRetryDelayMillis() {
            return retryDelayMillis;
        }

        public String getFrom() {
            return from;
        }

        public List<String> getTo() {
            return to;
        }
    }

    private GSuiteConfig gsuite;
    private LdapConfig ldap;
    private MailConfig mail;

    protected GSuiteSyncConfig() {
        // for deserialization
    }

    public GSuiteSyncConfig(GSuiteConfig gsuiteConfig, LdapConfig ldapConfig, MailConfig mailConfig) {
        this.gsuite = gsuiteConfig;
        this.ldap = ldapConfig;
        this.mail = mailConfig;
    }

    public GSuiteConfig getGsuiteConfig() {
        return gsuite;
    }

    public LdapConfig getLdapConfig() {
        return ldap;
    }

    public MailConfig getMailConfig() {
        return mail;
    }
}
