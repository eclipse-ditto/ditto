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
package org.eclipse.ditto.connectivity.service.messaging.internal.ssl;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.CredentialsVisitor;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;

/**
 * Factory class to create {@link java.security.KeyPair}s for SSH public key authentication.
 */
public final class PublicKeyAuthenticationFactory implements CredentialsVisitor<KeyPair> {

    private final ExceptionMapper exceptionMapper;

    /**
     * @return new instance with empty {@link org.eclipse.ditto.base.model.headers.DittoHeaders}
     */
    public static PublicKeyAuthenticationFactory getInstance() {
        return getInstance(DittoHeaders.empty());
    }

    /**
     * @param dittoHeaders the ditto headers
     * @return new instance of {@link KeyManagerFactoryFactory}
     */
    public static PublicKeyAuthenticationFactory getInstance(final DittoHeaders dittoHeaders) {
        return new PublicKeyAuthenticationFactory(dittoHeaders);
    }

    private PublicKeyAuthenticationFactory(final DittoHeaders dittoHeaders) {
        exceptionMapper = ExceptionMapper.forSshPublicKeyCredentials(dittoHeaders);
    }

    @Override
    public KeyPair clientCertificate(final ClientCertificateCredentials credentials) {
        throw new UnsupportedOperationException("ClientCertificateCredentials not supported");
    }

    @Override
    public KeyPair usernamePassword(final UserPasswordCredentials credentials) {
        throw new UnsupportedOperationException("UserPasswordCredentials not supported");
    }

    @Override
    public KeyPair sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
        final PrivateKey clientPrivateKey = Keys.getPrivateKey(credentials.getPrivateKey(), exceptionMapper);
        final PublicKey clientPublicKey = Keys.getPublicKey(credentials.getPublicKey(), exceptionMapper);
        return new KeyPair(clientPublicKey, clientPrivateKey);
    }

    @Override
    public KeyPair hmac(final HmacCredentials credentials) {
        throw new UnsupportedOperationException("HMAC not supported");
    }

    @Override
    public KeyPair oauthClientCredentials(final OAuthClientCredentials credentials) {
        throw new UnsupportedOperationException("OAuthClientCredentials not supported");
    }
}
