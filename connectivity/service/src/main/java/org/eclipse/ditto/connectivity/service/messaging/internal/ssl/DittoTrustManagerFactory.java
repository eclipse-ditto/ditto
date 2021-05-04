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

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;

/**
 * Simple wrapper around {@link TrustManagerFactory} that wraps the returned {@link TrustManager}s in
 * {@link DittoTrustManager}s and delegates all other invocations.
 */
public final class DittoTrustManagerFactory extends TrustManagerFactory {

    private static final TrustManagerFactoryFactory FACTORY =
            TrustManagerFactoryFactory.getInstance(DittoHeaders.empty());

    public static DittoTrustManagerFactory from(final Connection connection, final ConnectionLogger connectionLogger) {
        final String hostname = connection.getHostname();
        return new DittoTrustManagerFactory(FACTORY.newTrustManagerFactory(connection, true),
                FACTORY.newTrustManagerFactory(connection, false),
                hostname,
                connectionLogger);
    }

    private DittoTrustManagerFactory(final TrustManagerFactory delegateWithRevocationCheck,
            final TrustManagerFactory delegateWithoutRevocationCheck,
            final String hostname,
            final ConnectionLogger connectionLogger) {
        super(new TrustManagerFactorySpi() {
            @Override
            protected void engineInit(KeyStore keyStore) throws KeyStoreException {
                delegateWithRevocationCheck.init(keyStore);
                delegateWithoutRevocationCheck.init(keyStore);
            }

            @Override
            protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws
                    InvalidAlgorithmParameterException {
                delegateWithRevocationCheck.init(managerFactoryParameters);
                delegateWithoutRevocationCheck.init(managerFactoryParameters);
            }

            @Override
            protected TrustManager[] engineGetTrustManagers() {
                return DittoTrustManager.wrapTrustManagers(
                        delegateWithRevocationCheck.getTrustManagers(),
                        delegateWithoutRevocationCheck.getTrustManagers(),
                        hostname,
                        connectionLogger);
            }
        }, delegateWithRevocationCheck.getProvider(), delegateWithRevocationCheck.getAlgorithm());
    }
}
