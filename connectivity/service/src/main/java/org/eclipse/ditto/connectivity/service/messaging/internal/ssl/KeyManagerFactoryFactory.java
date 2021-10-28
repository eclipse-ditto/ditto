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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import javax.net.ssl.KeyManagerFactory;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.CredentialsVisitor;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;

/**
 * Factory class to create {@link javax.net.ssl.KeyManagerFactory}s.
 */
public final class KeyManagerFactoryFactory implements CredentialsVisitor<KeyManagerFactory> {


    private final KeyStoreFactory keyStoreFactory;
    private final ExceptionMapper exceptionMapper;

    /**
     * @return new instance with empty {@link DittoHeaders}
     */
    public static KeyManagerFactoryFactory getInstance() {
        return new KeyManagerFactoryFactory(ExceptionMapper.forClientCertificateCredentials());
    }

    /**
     * Instantiates a new {@link KeyManagerFactoryFactory}
     *
     * @param exceptionMapper the {@link ExceptionMapper} to be used
     */
    KeyManagerFactoryFactory(final ExceptionMapper exceptionMapper) {
        this.keyStoreFactory = new KeyStoreFactory(exceptionMapper);
        this.exceptionMapper = exceptionMapper;
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
        final PrivateKey privateKey = Keys.getPrivateKey(clientKeyPem, exceptionMapper);
        final Certificate certificate = Keys.getCertificate(clientCertificatePem, exceptionMapper);
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
    public KeyManagerFactory sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
        throw new UnsupportedOperationException(
                "Key pair authentication is not supported on certificate credentials authentication");
    }

    @Override
    public KeyManagerFactory hmac(final HmacCredentials credentials) {
        throw new UnsupportedOperationException("HMAC is not supported on certificate credentials authentication");
    }

    @Override
    public KeyManagerFactory oauthClientCredentials(final OAuthClientCredentials credentials) {
        throw new UnsupportedOperationException("OAuth client credentials is not supported on certificate credentials" +
                " authentication");
    }
}
