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

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;

import org.eclipse.ditto.connectivity.model.Credentials;

/**
 * Tests {@link SSLContextCreator}.
 * <p>
 * Certificates used by this test expires on 01 January 2100. Please regenerate certificates
 * according to {@link org.eclipse.ditto.connectivity.service.messaging.TestConstants.Certificates}.
 * </p>
 */
public final class SSLContextCreatorTest extends AbstractSSLContextTest {

    @Override
    SSLContext createSSLContext(@Nullable final String trustedCertificates,
            final String hostname, final Credentials credentials) {
        final SSLContextCreator sslContextCreator =
                SSLContextCreator.of(trustedCertificates, null, hostname, connectionLoggerMock);
        if (credentials == null) {
            return sslContextCreator.withoutClientCertificate();
        } else {
            return credentials.accept(sslContextCreator);
        }
    }

    @Override
    SSLContext createAcceptAnySSLContext() {
        return SSLContextCreator.withTrustManager(new AcceptAnyTrustManager(), null)
                .withoutClientCertificate();
    }
}
