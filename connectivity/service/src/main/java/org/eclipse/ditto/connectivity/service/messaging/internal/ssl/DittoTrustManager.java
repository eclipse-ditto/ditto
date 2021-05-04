/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.messaging.internal.ssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.regex.Pattern;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.InfoProviderFactory;

import sun.security.util.HostnameChecker;

/**
 * Trust manager with identity verification. The check succeeds if the certificate chain is valid and the given
 * hostname or IP can be verified.
 */
final class DittoTrustManager implements X509TrustManager {

    private static final HostnameChecker HOSTNAME_CHECKER = HostnameChecker.getInstance(HostnameChecker.TYPE_TLS);
    private static final Pattern IPV6_URI_PATTERN = Pattern.compile("^\\[[A-Fa-f0-9.:\\s]++]$");

    private final X509TrustManager delegateWithRevocationCheck;
    private final X509TrustManager delegateWithoutRevocationCheck;
    private final String hostnameOrIp;
    private final ConnectionLogger connectionLogger;

    private DittoTrustManager(final X509TrustManager delegateWithRevocationCheck,
            final X509TrustManager delegateWithoutRevocationCheck,
            final String hostnameOrIp,
            final ConnectionLogger connectionLogger) {
        this.delegateWithRevocationCheck = delegateWithRevocationCheck;
        this.delegateWithoutRevocationCheck = delegateWithoutRevocationCheck;
        this.hostnameOrIp = stripIpv6Brackets(hostnameOrIp);
        this.connectionLogger = connectionLogger;
    }

    /**
     * Create an array of {@code DittoTrustManager} from other trust managers.
     *
     * @param trustManagerWithRevocationCheck singleton array containing an X509 trust manager that performs revocation
     * checks.
     * @param trustManagerWithoutRevocationCheck singleton array containing an X509 trust manager that does not perform
     * revocation checks.
     * @param hostnameOrIP expected hostname or IP address in URI-embedded format.
     * @return array of trust managers such that all X509 trust managers are converted to Ditto trust managers.
     * @throws java.lang.IllegalArgumentException when the array arguments are not singletons.
     * @throws java.lang.ClassCastException when the trust managers are not X509 trust managers.
     */
    static TrustManager[] wrapTrustManagers(final TrustManager[] trustManagerWithRevocationCheck,
            final TrustManager[] trustManagerWithoutRevocationCheck,
            final String hostnameOrIP,
            final ConnectionLogger connectionLogger) {

        if (trustManagerWithRevocationCheck.length != 1 || trustManagerWithoutRevocationCheck.length != 1) {
            throw new IllegalArgumentException("Expect 1 trust manager with and without revocation check, got " +
                    trustManagerWithRevocationCheck.length + " and " + trustManagerWithoutRevocationCheck.length);
        }
        return new TrustManager[]{
                new DittoTrustManager((X509TrustManager) trustManagerWithRevocationCheck[0],
                        (X509TrustManager) trustManagerWithoutRevocationCheck[0],
                        hostnameOrIP,
                        connectionLogger)
        };
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String s) throws CertificateException {
        delegateWithRevocationCheck.checkClientTrusted(chain, s);
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {

        // verify certificate chain
        try {
            delegateWithRevocationCheck.checkServerTrusted(chain, authType);
        } catch (final CertificateException e) {
            // check again without revocation to detect any masked errors
            delegateWithoutRevocationCheck.checkServerTrusted(chain, authType);
            connectionLogger.exception(InfoProviderFactory.empty(), e);
        }

        // verify hostname
        if (chain.length <= 0) {
            throw new CertificateException("Cannot verify hostname - empty certificate chain");
        }

        // first certificate is the server certificate (from rfc-5246: "This is a sequence (chain) of certificates. The
        // sender's certificate MUST come first in the list.")
        final X509Certificate serverCertificate = chain[0];
        if (!isServerCertificateInTrustStore(serverCertificate)) {
            HOSTNAME_CHECKER.match(hostnameOrIp, serverCertificate);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegateWithRevocationCheck.getAcceptedIssuers();
    }

    private boolean isServerCertificateInTrustStore(final X509Certificate serverCertificate) {
        return Arrays.asList(delegateWithRevocationCheck.getAcceptedIssuers()).contains(serverCertificate);
    }

    private static String stripIpv6Brackets(final String hostnameOrIp) {
        if (IPV6_URI_PATTERN.matcher(hostnameOrIp).matches()) {
            return hostnameOrIp.substring(1, hostnameOrIp.length() - 1);
        } else {
            return hostnameOrIp;
        }
    }
}
