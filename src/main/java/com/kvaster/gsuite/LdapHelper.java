package com.kvaster.gsuite;

import java.net.URI;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.ExtendedRequest;
import com.unboundid.ldap.sdk.ExtendedResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.SSLUtil;

public class LdapHelper {
    private static final String SCHEME_LDAP = "ldap";
    private static final String SCHEME_LDAPS = "ldaps";

    private final String ldapHost;
    private final int ldapPort;
    private final boolean isLdaps;

    private final ExtendedRequest startTlsExtRequest;
    private final SSLSocketFactory sslSocketFactory;

    public LdapHelper(String url) throws GeneralSecurityException, LDAPException {
        URI ldapUri = URI.create(url);

        boolean isLdaps = false;
        if (SCHEME_LDAPS.equals(ldapUri.getScheme())) {
            isLdaps = true;
        } else if (!SCHEME_LDAP.equals(ldapUri.getScheme())) {
            throw new IllegalArgumentException("Wrong scheme");
        }

        ldapHost = ldapUri.getHost();
        ldapPort = ldapUri.getPort() < 0 ? (isLdaps ? 636: 389) : ldapUri.getPort();
        this.isLdaps = isLdaps;

        SSLUtil sslUtil = new SSLUtil();
        SSLContext context = sslUtil.createSSLContext();
        startTlsExtRequest = new StartTLSExtendedRequest(context);
        sslSocketFactory = sslUtil.createSSLSocketFactory();
    }

    public LDAPConnection connect() throws LDAPException {
        LDAPConnection c = new LDAPConnection();

        try {
            if (isLdaps) {
                c.setSocketFactory(sslSocketFactory);
            }

            c.connect(ldapHost, ldapPort);

            if (!isLdaps) {
                ExtendedResult er = c.processExtendedOperation(startTlsExtRequest);
                if (er.getResultCode() != ResultCode.SUCCESS) {
                    throw new LDAPException(er);
                }
            }

            return c;
        } catch (LDAPException e) {
            c.close();
            throw e;
        }
    }

    public LDAPConnection connect(String bindDn, String bindPw) throws LDAPException {
        LDAPConnection c = connect();

        try {
            BindResult br = c.bind(bindDn, bindPw);

            if (br.getResultCode() != ResultCode.SUCCESS) {
                throw new LDAPException(br);
            }

            return c;
        } catch (LDAPException e) {
            c.close();
            throw e;
        }
    }
}
