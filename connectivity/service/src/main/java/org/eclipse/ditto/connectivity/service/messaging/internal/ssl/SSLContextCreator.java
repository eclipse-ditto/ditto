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

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.CredentialsVisitor;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;

/**
 * Create SSL context from connection {@link org.eclipse.ditto.connectivity.model.ClientCertificateCredentials}.
 */
public final class SSLContextCreator implements CredentialsVisitor<SSLContext> {

    static final String TLS12 = "TLSv1.2";

    @Nullable private final TrustManager trustManager;
    private final ExceptionMapper exceptionMapper;
    private final String hostname;
    private final KeyManagerFactoryFactory keyManagerFactoryFactory;
    @Nullable private final TrustManagerFactoryFactory trustManagerFactoryFactory;
    @Nullable private final String trustedCertificates;
    private final ConnectionLogger connectionLogger;

    private SSLContextCreator(@Nullable final String trustedCertificates,
            @Nullable final DittoHeaders dittoHeaders,
            final String hostname,
            final ConnectionLogger connectionLogger) {
        exceptionMapper = ExceptionMapper.forClientCertificateCredentials(dittoHeaders);
        this.hostname = hostname;
        keyManagerFactoryFactory = new KeyManagerFactoryFactory(exceptionMapper);
        trustManagerFactoryFactory = TrustManagerFactoryFactory.getInstance(exceptionMapper);
        this.trustedCertificates = trustedCertificates;
        this.connectionLogger = connectionLogger;
        trustManager = null;
    }

    private SSLContextCreator(final TrustManager trustManager,
            @Nullable final DittoHeaders dittoHeaders,
            @Nullable final String hostname) {
        exceptionMapper = ExceptionMapper.forClientCertificateCredentials(dittoHeaders);
        this.hostname = hostname;
        keyManagerFactoryFactory = new KeyManagerFactoryFactory(exceptionMapper);
        trustManagerFactoryFactory = null;
        trustedCertificates = null;
        connectionLogger = null;
        this.trustManager = trustManager;
    }

    /**
     * Create an SSL context creator with a preconfigured trust manager.
     *
     * @param trustManager the trust manager.
     * @param dittoHeaders headers to write in Ditto runtime exceptions; {@code null} to write empty headers.
     * @return the SSL context creator.
     */
    public static SSLContextCreator withTrustManager(final TrustManager trustManager,
            @Nullable final DittoHeaders dittoHeaders) {
        return new SSLContextCreator(trustManager, dittoHeaders, null);
    }

    /**
     * Create an SSL context creator that verifies server hostname but accepts all IP addresses.
     *
     * @param connection connection for which to create SSLContext.
     * @param dittoHeaders headers to write in Ditto runtime exceptions; {@code null} to write empty headers.
     * @param connectionLogger used to log failures during certificate validation.
     * @return the SSL context creator.
     */
    public static SSLContextCreator fromConnection(final Connection connection,
            @Nullable final DittoHeaders dittoHeaders, final ConnectionLogger connectionLogger) {
        final boolean isValidateCertificates = connection.isValidateCertificates();
        if (isValidateCertificates) {
            final String trustedCertificates = connection.getTrustedCertificates().orElse(null);
            return of(trustedCertificates, dittoHeaders, connection.getHostname(), connectionLogger);
        } else {
            return withTrustManager(new AcceptAnyTrustManager(), dittoHeaders);
        }
    }

    /**
     * Create an SSL context creator that verifies server identity but accepts all IP addresses.
     *
     * @param trustedCertificates certificates to trust; {@code null} to trust the standard certificate authorities.
     * @param dittoHeaders headers to write in Ditto runtime exceptions; {@code null} to write empty headers.
     * @param hostnameOrIp hostname to verify in server certificate or a nullable IP address to not verify hostname.
     * @param connectionLogger used to log failures during certificate validation.
     * @return the SSL context creator.
     */
    public static SSLContextCreator of(@Nullable final String trustedCertificates,
            @Nullable final DittoHeaders dittoHeaders,
            final String hostnameOrIp,
            final ConnectionLogger connectionLogger) {
        return new SSLContextCreator(trustedCertificates, dittoHeaders, hostnameOrIp, connectionLogger);
    }

    @Override
    public SSLContext clientCertificate(final ClientCertificateCredentials credentials) {
        final String clientKeyPem = credentials.getClientKey().orElse(null);
        final String clientCertificatePem = credentials.getClientCertificate().orElse(null);

        final KeyManager[] keyManagers;
        if (clientKeyPem != null && clientCertificatePem != null) {
            final KeyManagerFactory keyManagerFactory;
            keyManagerFactory = keyManagerFactoryFactory.newKeyManagerFactory(clientKeyPem, clientCertificatePem);
            keyManagers = keyManagerFactory.getKeyManagers();
        } else {
            keyManagers = null;
        }

        final TrustManager[] trustManagers;
        if (trustManagerFactoryFactory != null) {
            final TrustManagerFactory withRevocationCheck =
                    trustManagerFactoryFactory.newTrustManagerFactory(trustedCertificates, true);
            final TrustManagerFactory withoutRevocationCheck =
                    trustManagerFactoryFactory.newTrustManagerFactory(trustedCertificates, false);
            trustManagers = DittoTrustManager.wrapTrustManagers(
                    withRevocationCheck.getTrustManagers(),
                    withoutRevocationCheck.getTrustManagers(),
                    hostname,
                    connectionLogger
            );
        } else if (trustManager != null) {
            trustManagers = new TrustManager[]{trustManager};
        } else {
            trustManagers = null;
        }

        return newTLSContext(keyManagers, trustManagers);
    }

    @Override
    public SSLContext usernamePassword(final UserPasswordCredentials credentials) {
        return withoutClientCertificate();
    }

    @Override
    public SSLContext sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
        return withoutClientCertificate();
    }

    @Override
    public SSLContext hmac(final HmacCredentials credentials) {
        return withoutClientCertificate();
    }

    @Override
    public SSLContext oauthClientCredentials(final OAuthClientCredentials credentials) {
        return withoutClientCertificate();
    }

    /**
     * Create an SSL context with trusted certificates without client authentication.
     *
     * @return the SSL context
     */
    public SSLContext withoutClientCertificate() {
        return clientCertificate(ClientCertificateCredentials.empty());
    }

    private SSLContext newTLSContext(@Nullable final KeyManager[] keyManagers,
            @Nullable final TrustManager[] trustManagers) {
        try {
            final SSLContext sslContext = SSLContext.getInstance(TLS12);
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (final NoSuchAlgorithmException | KeyManagementException e) {
            throw exceptionMapper.fatalError("Cannot start TLS 1.2 engine")
                    .cause(e)
                    .build();
        }
    }
}
