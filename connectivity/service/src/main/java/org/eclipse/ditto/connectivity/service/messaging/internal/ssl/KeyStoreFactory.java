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
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;

final class KeyStoreFactory {

    private final ExceptionMapper exceptionMapper;

    KeyStoreFactory(final ExceptionMapper exceptionMapper) {
        this.exceptionMapper = exceptionMapper;
    }

    KeyStore newKeystore() {
        try {
            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            // initialize an empty keystore
            keyStore.load(null, null);
            return keyStore;
        } catch (final Exception e) {
            throw exceptionMapper.fatalError("Cannot initialize client-side security for connection")
                    .cause(e)
                    .build();
        }
    }

    void setPrivateKey(final KeyStore keystore, final PrivateKey privateKey,
            final Certificate... certs) {
        try {
            keystore.setKeyEntry("key", privateKey, new char[0], certs);
        } catch (final KeyStoreException e) {
            throw exceptionMapper.fatalError("Engine failed to configure client key")
                    .cause(e)
                    .build();
        }
    }

    void setCertificate(final KeyStore keystore, final Certificate certificate) {
        try {
            keystore.setCertificateEntry("cert", certificate);
        } catch (final KeyStoreException e) {
            throw exceptionMapper.fatalError("Engine failed to configure client certificate")
                    .cause(e)
                    .build();
        }
    }

}
