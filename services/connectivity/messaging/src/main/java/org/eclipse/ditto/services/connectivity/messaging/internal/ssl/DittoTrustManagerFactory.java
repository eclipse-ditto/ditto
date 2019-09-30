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

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;

import javax.annotation.Nullable;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;

import org.eclipse.ditto.model.connectivity.Connection;

/**
 * Simple wrapper around {@link TrustManagerFactory} that wraps the returned {@link TrustManager}s in
 * {@link DittoTrustManager}s and delegates all other invocations.
 */
public final class DittoTrustManagerFactory extends TrustManagerFactory {

    private static final TrustManagerFactoryFactory FACTORY = TrustManagerFactoryFactory.getInstance();

    public static DittoTrustManagerFactory from(final Connection connection) {
        final String hostname = connection.getHostname();
        return new DittoTrustManagerFactory(FACTORY.newTrustManagerFactory(connection), hostname);
    }

    static DittoTrustManagerFactory from(@Nullable final String trustedCertificates, final String hostname) {
        return new DittoTrustManagerFactory(FACTORY.newTrustManagerFactory(trustedCertificates), hostname);
    }

    private DittoTrustManagerFactory(final TrustManagerFactory delegate, final String hostname) {
        super(new TrustManagerFactorySpi() {
            @Override
            protected void engineInit(KeyStore keyStore) throws KeyStoreException {
                delegate.init(keyStore);
            }

            @Override
            protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws
                    InvalidAlgorithmParameterException {
                delegate.init(managerFactoryParameters);
            }

            @Override
            protected TrustManager[] engineGetTrustManagers() {
                return DittoTrustManager.wrapTrustManagers(delegate.getTrustManagers(), hostname);
            }
        }, delegate.getProvider(), delegate.getAlgorithm());
    }
}