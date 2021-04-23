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

import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;

/**
 * TrustManager that accepts any certificate.
 * This trust manager should only be used for local development and test systems !!
 */
public final class AcceptAnyTrustManager implements X509TrustManager {

    /**
     * Create a trust manager factory that only creates this trust manager.
     *
     * @param delegate the delegate to provide provider and algorithms.
     * @return the accept-any trust manager factory.
     */
    public static TrustManagerFactory factory(final TrustManagerFactory delegate) {
        return new Factory(delegate);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    @Override
    @SuppressWarnings("squid:S4424") // ignore SSL security on purpose
    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
        // do not check
    }

    @Override
    @SuppressWarnings("squid:S4424") // ignore SSL security on purpose
    public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
        // do not check
    }

    private static final class Factory extends TrustManagerFactory {

        private Factory(final TrustManagerFactory delegate) {
            super(new TrustManagerFactorySpi() {
                @Override
                protected void engineInit(KeyStore keyStore) {
                }

                @Override
                protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
                }

                @Override
                protected TrustManager[] engineGetTrustManagers() {
                    return new TrustManager[]{new AcceptAnyTrustManager()};
                }
            }, delegate.getProvider(), delegate.getAlgorithm());
        }
    }
}
