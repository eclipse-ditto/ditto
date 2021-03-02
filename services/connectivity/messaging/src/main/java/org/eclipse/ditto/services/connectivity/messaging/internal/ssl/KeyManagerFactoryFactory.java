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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import javax.net.ssl.KeyManagerFactory;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.CredentialsVisitor;
import org.eclipse.ditto.model.connectivity.KeyPairCredentials;
import org.eclipse.ditto.model.connectivity.UserPasswordCredentials;
import org.eclipse.ditto.services.connectivity.messaging.internal.ExceptionMapper;
import org.eclipse.ditto.services.connectivity.messaging.internal.KeyExtractor;

/**
 * Factory class to create {@link javax.net.ssl.KeyManagerFactory}s.
 */
public final class KeyManagerFactoryFactory extends KeyExtractor implements CredentialsVisitor<KeyManagerFactory> {

    private static final JsonPointer publicKeyErrorLocation = Connection.JsonFields.CREDENTIALS.getPointer()
            .append(ClientCertificateCredentials.JsonFields.CLIENT_KEY.getPointer());
    private static final JsonPointer privateKeyErrorLocation = Connection.JsonFields.CREDENTIALS.getPointer()
            .append(ClientCertificateCredentials.JsonFields.CLIENT_KEY.getPointer());
    private KeyStoreFactory keyStoreFactory;

    /**
     * @return new instance with empty {@link DittoHeaders}
     */
    public static KeyManagerFactoryFactory getInstance() {
        return new KeyManagerFactoryFactory(new ExceptionMapper(DittoHeaders.empty()));
    }

    /**
     * @param dittoHeaders the ditto headers
     * @return new instance of {@link KeyManagerFactoryFactory}
     */
    public static KeyManagerFactoryFactory getInstance(final DittoHeaders dittoHeaders) {
        return new KeyManagerFactoryFactory(new ExceptionMapper(dittoHeaders));
    }

    /**
     * Instantiates a new {@link KeyManagerFactoryFactory}
     *
     * @param exceptionMapper the {@link ExceptionMapper} to be used
     */
    KeyManagerFactoryFactory(final ExceptionMapper exceptionMapper) {
        super(exceptionMapper, publicKeyErrorLocation, privateKeyErrorLocation);
        this.keyStoreFactory = new KeyStoreFactory(exceptionMapper);
    }

    /**
     * @param clientKeyPem the client key in PEM format
     * @param clientCertificatePem the client certificate in PEM
     * @return the new {@link KeyManagerFactory}
     */
    KeyManagerFactory newKeyManagerFactory(final String clientKeyPem, final String clientCertificatePem) {
        checkNotNull(clientKeyPem, "clientKeyPem");
        checkNotNull(clientCertificatePem, "clientCertificatePem");

        final KeyStore keystore = keyStoreFactory.newKeystore();
        final PrivateKey privateKey = getClientPrivateKey(clientKeyPem);
        final Certificate certificate = getClientCertificate(clientCertificatePem);
        keyStoreFactory.setPrivateKey(keystore, privateKey, certificate);
        keyStoreFactory.setCertificate(keystore, certificate);

        try {
            final KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, new char[0]);
            return keyManagerFactory;
        } catch (final Exception e) {
            throw exceptionMapper.fatalError("Engine failed to configure client key and client certificate")
                    .cause(e)
                    .build();
        }
    }

    @Override
    public KeyManagerFactory clientCertificate(final ClientCertificateCredentials credentials) {
        final String clientKeyPem = credentials.getClientKey().orElse(null);
        final String clientCertificatePem = credentials.getClientCertificate().orElse(null);

        if (clientKeyPem != null && clientCertificatePem != null) {
            return newKeyManagerFactory(clientKeyPem, clientCertificatePem);
        } else {
            throw exceptionMapper.fatalError("Either client key or certificate were missing").build();
        }
    }

    @Override
    public KeyManagerFactory usernamePassword(final UserPasswordCredentials credentials) {
        throw new UnsupportedOperationException("Username password authentication is not supported on certificate " +
                "credentials authentication");
    }

    @Override
    public KeyManagerFactory keyPair(final KeyPairCredentials credentials) {
        throw new UnsupportedOperationException(
                "Key pair authentication is not supported on certificate " +
                        "credentials authentication");
    }
}
