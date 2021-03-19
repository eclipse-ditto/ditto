/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.security.cert.Certificate;

import org.eclipse.ditto.json.JsonPointer;

/**
 * Implementation of KeyExtractor to load test certificates.
 *
 * @see org.eclipse.ditto.services.connectivity.messaging.TestConstants.Certificates
 */
public class TestCertificates extends KeyExtractor {

    public TestCertificates() {
        super(new ExceptionMapper(null), JsonPointer.empty(), JsonPointer.empty());
    }

    public Certificate getPrivateKey(final String privateKeyPem) {
        return getClientCertificate(privateKeyPem);
    }

    public Certificate getPublicKey(final String publicKeyPem) {
        return getClientCertificate(publicKeyPem);
    }

    public Certificate getCertificate(final String certificatePem) {
        return getClientCertificate(certificatePem);
    }
}
