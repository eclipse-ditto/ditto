/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * TrustManager that accepts any certificate.
 * This trust manager should only be used for local development and test systems !!
 */
public class AcceptAnyTrustManager implements X509TrustManager {

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
