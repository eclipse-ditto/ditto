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
package org.eclipse.ditto.services.connectivity.messaging.tunnel;

import org.apache.sshd.client.session.ClientSession;
import org.eclipse.ditto.model.connectivity.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.CredentialsVisitor;
import org.eclipse.ditto.model.connectivity.KeyPairCredentials;
import org.eclipse.ditto.model.connectivity.UserPasswordCredentials;

/**
 * TODO
 */
class ClientSessionCredentialsVisitor implements CredentialsVisitor<Void> {

    ClientSessionCredentialsVisitor(final ClientSession clientSession) {
        this.clientSession = clientSession;
    }

    private final ClientSession clientSession;

    @Override
    public Void clientCertificate(final ClientCertificateCredentials credentials) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public Void usernamePassword(final UserPasswordCredentials credentials) {
        clientSession.addPasswordIdentity(credentials.getPassword());
        return null;
    }

    @Override
    public Void keyPair(final KeyPairCredentials credentials) {
        // TODO
        throw new UnsupportedOperationException();
    }
}
