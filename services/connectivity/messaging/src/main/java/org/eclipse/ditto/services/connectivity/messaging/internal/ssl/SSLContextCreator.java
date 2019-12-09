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

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.CredentialsVisitor;

/**
 * Create SSL context from connection credentials.
 */
public final class SSLContextCreator implements CredentialsVisitor<SSLContext> {

    static final String TLS12 = "TLSv1.2";

    private final TrustManager trustManager;
    private final ExceptionMapper exceptionMapper;
    private final String hostname;
    private final KeyManagerFactoryFactory keyManagerFactoryFactory;
    private final TrustManagerFactory trustManagerFactory;
    private final TrustManagerFactoryFactory trustManagerFactoryFactory;

    private SSLContextCreator(@Nullable final String trustedCertificates,
            @Nullable final DittoHeaders dittoHeaders,
            @Nullable String hostname) {
        this.exceptionMapper = new ExceptionMapper(dittoHeaders);
        this.hostname = hostname;
        this.keyManagerFactoryFactory = new KeyManagerFactoryFactory(exceptionMapper);
        this.trustManagerFactoryFactory = new TrustManagerFactoryFactory(exceptionMapper);
        this.trustManagerFactory = trustManagerFactoryFactory.newTrustManagerFactory(trustedCertificates);
        this.trustManager = null;
    }

    private SSLContextCreator(final TrustManager trustManager,
            @Nullable final DittoHeaders dittoHeaders,
            @Nullable String hostname) {
        this.exceptionMapper = new ExceptionMapper(dittoHeaders);
        this.hostname = hostname;
        this.keyManagerFactoryFactory = new KeyManagerFactoryFactory(exceptionMapper);
        this.trustManagerFactoryFactory = new TrustManagerFactoryFactory(exceptionMapper);
        this.trustManagerFactory = null;
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
     * @return the SSL context creator.
     */
    public static SSLContextCreator fromConnection(final Connection connection,
            @Nullable final DittoHeaders dittoHeaders) {
        final String trustedCertificates = connection.getTrustedCertificates().orElse(null);
        return of(trustedCertificates, dittoHeaders, connection.getHostname());
    }

    /**
     * Create an SSL context creator that verifies server identity but accepts all IP addresses.
     *
     * @param trustedCertificates certificates to trust; {@code null} to trust the standard certificate authorities.
     * @param dittoHeaders headers to write in Ditto runtime exceptions; {@code null} to write empty headers.
     * @param hostnameOrIp hostname to verify in server certificate or a nullable IP address to not verify hostname.
     * @return the SSL context creator.
     */
    public static SSLContextCreator of(@Nullable final String trustedCertificates,
            @Nullable final DittoHeaders dittoHeaders,
            @Nullable final String hostnameOrIp) {
        return new SSLContextCreator(trustedCertificates, dittoHeaders, hostnameOrIp);
    }

    @Override
    public SSLContext clientCertificate(@Nonnull final ClientCertificateCredentials credentials) {

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
        if (trustManagerFactory != null) {
            trustManagers = DittoTrustManager.wrapTrustManagers(trustManagerFactory.getTrustManagers(), hostname);
        }  else if (trustManager != null) {
            trustManagers = new TrustManager[] {trustManager};
        } else {
            throw new IllegalArgumentException("cannot happen");
        }

        return newTLSContext(keyManagers, trustManagers);
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