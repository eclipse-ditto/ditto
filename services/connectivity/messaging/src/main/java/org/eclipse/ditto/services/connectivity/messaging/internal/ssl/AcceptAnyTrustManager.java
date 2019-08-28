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

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * TrustManager that accepts any certificate.
 * This trust manager should only be used for local development and test systems !!
 */
public final class AcceptAnyTrustManager implements X509TrustManager {

    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }

    @SuppressWarnings("squid:S4424") // ignore SSL security on purpose
    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
        // do not check
    }

    @SuppressWarnings("squid:S4424") // ignore SSL security on purpose
    public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
        // do not check
    }
}
