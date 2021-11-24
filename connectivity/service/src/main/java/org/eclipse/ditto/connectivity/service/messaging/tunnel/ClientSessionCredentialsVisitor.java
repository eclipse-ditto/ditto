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
package org.eclipse.ditto.connectivity.service.messaging.tunnel;

import java.security.KeyPair;
import java.util.Collections;

import org.apache.sshd.client.session.ClientSession;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.CredentialsVisitor;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.PublicKeyAuthenticationFactory;

import akka.event.LoggingAdapter;

/**
 * Applies configured {@link org.eclipse.ditto.connectivity.model.Credentials} from a connection to the given
 * {@link org.apache.sshd.client.session.ClientSession}.
 */
class ClientSessionCredentialsVisitor implements CredentialsVisitor<Void> {

    private final CredentialsVisitor<KeyPair> publicKeyAuthenticationFactory;
    private final LoggingAdapter logger;

    ClientSessionCredentialsVisitor(final ClientSession clientSession, final LoggingAdapter logger) {
        this.logger = logger;
        publicKeyAuthenticationFactory = PublicKeyAuthenticationFactory.getInstance();
        this.clientSession = clientSession;
    }

    private final ClientSession clientSession;

    @Override
    public Void clientCertificate(final ClientCertificateCredentials credentials) {
        // not supported
        return null;
    }

    @Override
    public Void usernamePassword(final UserPasswordCredentials credentials) {
        logger.debug("Setting password identity on session.");
        final String password = credentials.getPassword();
        clientSession.setPasswordIdentityProvider(sessionContext -> Collections.singleton(password));
        return null;
    }

    @Override
    public Void sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
        logger.debug("Setting public key identity on session.");
        final KeyPair keyPair = credentials.accept(publicKeyAuthenticationFactory);
        clientSession.setKeyIdentityProvider(session -> Collections.singleton(keyPair));
        return null;
    }

    @Override
    public Void hmac(final HmacCredentials credentials) {
        // not supported
        return null;
    }

    @Override
    public Void oauthClientCredentials(final OAuthClientCredentials credentials) {
        // not supported
        return null;
    }
}
