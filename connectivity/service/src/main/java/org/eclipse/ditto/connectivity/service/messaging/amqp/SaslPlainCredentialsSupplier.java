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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import java.util.Optional;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Credentials;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;

import akka.actor.ActorSystem;

/**
 * Computes any username and password to authenticate an AMQP connection via SASL PLAIN.
 */
final class SaslPlainCredentialsSupplier implements PlainCredentialsSupplier {

    private static final PlainCredentialsSupplier FROM_URI = PlainCredentialsSupplier.fromUri();

    private final AmqpConnectionSigningExtension amqpConnectionSigningExtension;

    private SaslPlainCredentialsSupplier(final ActorSystem actorSystem) {
        amqpConnectionSigningExtension = AmqpConnectionSigningExtension.get(actorSystem);
    }

    public static PlainCredentialsSupplier of(final ActorSystem actorSystem) {
        return new SaslPlainCredentialsSupplier(actorSystem);
    }

    @Override
    public Optional<UserPasswordCredentials> get(final Connection connection) {
        final Optional<Credentials> optionalCredentials = connection.getCredentials();
        if (optionalCredentials.isPresent()) {
            final var credentials = optionalCredentials.get();
            final var requestSigning = credentials.accept(amqpConnectionSigningExtension);
            return requestSigning.createSignedCredentials().or(() -> FROM_URI.get(connection));
        }
        return FROM_URI.get(connection);
    }
}
