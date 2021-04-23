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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Credentials;

/**
 * Tests an {@link SSLContext} created from a {@link TrustManagerFactory} and {@link KeyManagerFactory} using the
 * {@link TrustManagerFactoryFactory} and {@link KeyManagerFactoryFactory}.
 */
public final class CustomSSLContextTest extends AbstractSSLContextTest {

    @Override
    SSLContext createSSLContext(@Nullable final String trustedCertificates,
            final String hostname, final Credentials credentials) throws Exception {

        final Connection connection = mock(Connection.class);
        when(connection.getTrustedCertificates()).thenReturn(Optional.ofNullable(trustedCertificates));
        when(connection.isValidateCertificates()).thenReturn(true);
        when(connection.getHostname()).thenReturn(hostname);
        final TrustManagerFactory trustManagerFactory = DittoTrustManagerFactory.from(connection, connectionLoggerMock);

        final KeyManager[] keyManagers = Optional.ofNullable(credentials)
                .map(c -> c.accept(KeyManagerFactoryFactory.getInstance()))
                .map(KeyManagerFactory::getKeyManagers)
                .orElse(null);

        final SSLContext sslContext = SSLContext.getInstance(SSLContextCreator.TLS12);
        sslContext.init(keyManagers, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    @Override
    SSLContext createAcceptAnySSLContext() throws Exception {
        final SSLContext sslContext = SSLContext.getInstance(SSLContextCreator.TLS12);
        final TrustManagerFactory trustManagerFactory =
                TrustManagerFactoryFactory.getInstance(DittoHeaders.empty()).newInsecureTrustManagerFactory();
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }
}
