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

import org.eclipse.ditto.model.connectivity.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.CredentialsVisitor;
import org.eclipse.ditto.model.connectivity.KeyPairCredentials;
import org.eclipse.ditto.model.connectivity.UserPasswordCredentials;

/**
 * Factory class to create {@link javax.net.ssl.KeyManagerFactory}s.
 */
public final class UsernamePasswordPairCreator implements CredentialsVisitor<UsernamePasswordPair> {

    @Override
    public UsernamePasswordPair clientCertificate(final ClientCertificateCredentials credentials) {
        throw new UnsupportedOperationException(
                "Certificate credentials authentication is not supported on username password authentication");
    }

    @Override
    public UsernamePasswordPair usernamePassword(final UserPasswordCredentials credentials) {
        return new UsernamePasswordPair(credentials.getUsername(), credentials.getPassword());
    }

    @Override
    public UsernamePasswordPair keyPair(final KeyPairCredentials credentials) {
        throw new UnsupportedOperationException(
                "Key pair authentication is not supported on username password authentication");
    }

}
