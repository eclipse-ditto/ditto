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
package org.eclipse.ditto.services.connectivity.messaging.internal.ssh;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.CredentialsVisitor;
import org.eclipse.ditto.model.connectivity.KeyPairCredentials;
import org.eclipse.ditto.model.connectivity.SshTunnel;
import org.eclipse.ditto.model.connectivity.UserPasswordCredentials;
import org.eclipse.ditto.services.connectivity.messaging.internal.ExceptionMapper;
import org.eclipse.ditto.services.connectivity.messaging.internal.KeyExtractor;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.KeyManagerFactoryFactory;

/**
 * Factory class to create {@link java.security.KeyPair}s.
 */
public final class KeyPairCreator extends KeyExtractor implements CredentialsVisitor<KeyPair> {

    private static final JsonPointer publicKeyErrorLocation = Connection.JsonFields.SSH_TUNNEL.getPointer()
            .append(SshTunnel.JsonFields.CREDENTIALS.getPointer())
            .append(KeyPairCredentials.JsonFields.PUBLIC_KEY.getPointer());
    private static final JsonPointer privateKeyErrorLocation = Connection.JsonFields.SSH_TUNNEL.getPointer()
            .append(SshTunnel.JsonFields.CREDENTIALS.getPointer())
            .append(KeyPairCredentials.JsonFields.PRIVATE_KEY.getPointer());

    /**
     * @return new instance with empty {@link org.eclipse.ditto.model.base.headers.DittoHeaders}
     */
    public static KeyPairCreator getInstance() {
        return new KeyPairCreator(new ExceptionMapper(DittoHeaders.empty()));
    }

    /**
     * @param dittoHeaders the ditto headers
     * @return new instance of {@link KeyManagerFactoryFactory}
     */
    public static KeyPairCreator getInstance(final DittoHeaders dittoHeaders) {
        return new KeyPairCreator(new ExceptionMapper(dittoHeaders));
    }


    private KeyPairCreator(final ExceptionMapper exceptionMapper) {
        super(exceptionMapper, publicKeyErrorLocation, privateKeyErrorLocation);
    }

    @Override
    public KeyPair clientCertificate(final ClientCertificateCredentials credentials) {
        throw new UnsupportedOperationException(
                "Certificate credentials authentication is not supported on key pair authentication");
    }

    @Override
    public KeyPair usernamePassword(final UserPasswordCredentials credentials) {
        throw new UnsupportedOperationException("Username password authentication is not supported on key pair " +
                "authentication");
    }

    @Override
    public KeyPair keyPair(final KeyPairCredentials credentials) {
        final PrivateKey clientPrivateKey = getClientPrivateKey(credentials.getPrivateKey());
        final PublicKey clientPublicKey = getClientPublicKey(credentials.getPublicKey());
        return new KeyPair(clientPublicKey, clientPrivateKey);
    }

}
