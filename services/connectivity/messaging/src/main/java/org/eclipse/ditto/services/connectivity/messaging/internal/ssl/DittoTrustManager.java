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
package org.eclipse.ditto.services.connectivity.messaging.internal.ssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import sun.security.util.HostnameChecker;

/**
 * Trust manager with identity verification. The check succeeds if the certificate chain is valid and the given
 * hostname or IP can be verified.
 */
final class DittoTrustManager implements X509TrustManager {

    private static final HostnameChecker HOSTNAME_CHECKER = HostnameChecker.getInstance(HostnameChecker.TYPE_TLS);
    private static final Pattern IPV6_URI_PATTERN = Pattern.compile("^\\[[A-Fa-f0-9.:\\s]++]$");

    private final X509TrustManager delegate;
    private final String hostnameOrIp;

    private DittoTrustManager(final X509TrustManager delegate, final String hostnameOrIp) {
        this.delegate = delegate;
        this.hostnameOrIp = stripIpv6Brackets(hostnameOrIp);
    }

    /**
     * Create an array of {@code DittoTrustManager} from other trust managers.
     *
     * @param trustManagers trust managers to convert.
     * @param hostnameOrIp expected hostname or IP address in URI-embedded format.
     * @return array of trust managers such that all X509 trust managers are converted to Ditto trust managers.
     */
    static TrustManager[] wrapTrustManagers(final TrustManager[] trustManagers,
            @Nullable final String hostnameOrIp) {

        if (hostnameOrIp != null) {
            return Arrays.stream(trustManagers)
                    .map(trustManager -> trustManager instanceof X509TrustManager
                            ? new DittoTrustManager((X509TrustManager) trustManager, hostnameOrIp)
                            : trustManager)
                    .toArray(TrustManager[]::new);
        } else {
            return trustManagers;
        }
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String s) throws CertificateException {
        delegate.checkClientTrusted(chain, s);
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {

        // verify certificate chain
        delegate.checkServerTrusted(chain, authType);

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
        return delegate.getAcceptedIssuers();
    }

    private boolean isServerCertificateInTrustStore(final X509Certificate serverCertificate) {
        return Arrays.asList(delegate.getAcceptedIssuers()).contains(serverCertificate);
    }

    @Nullable
    private static String stripIpv6Brackets(@Nullable final String hostnameOrIp) {
        if (hostnameOrIp != null && IPV6_URI_PATTERN.matcher(hostnameOrIp).matches()) {
            return hostnameOrIp.substring(1, hostnameOrIp.length() - 1);
        } else {
            return hostnameOrIp;
        }
    }
}
