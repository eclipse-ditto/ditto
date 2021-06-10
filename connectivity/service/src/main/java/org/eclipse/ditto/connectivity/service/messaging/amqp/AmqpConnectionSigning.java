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

import java.time.Instant;
import java.util.Optional;

import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.messaging.signing.Signing;

/**
 * Functional interface for preparing signing information for AMQP connection.
 *
 * @since 2.1.0
 */
@FunctionalInterface
public interface AmqpConnectionSigning extends Signing {

    /**
     * Creates signed credentials for a connection if applicable.
     *
     * @return an {@link Optional} containing the signed credentials if applicable, otherwise an empty {@link Optional}.
     */
    default Optional<UserPasswordCredentials> createSignedCredentials() {
        return createSignedCredentials(Instant.now());
    }

    /**
     * Creates signed credentials for a connection if applicable.
     *
     * @param timestamp Timestamp to include in the signature.
     * @return an {@link Optional} containing the signed credentials if applicable, otherwise an empty {@link Optional}.
     */
    Optional<UserPasswordCredentials> createSignedCredentials(Instant timestamp);

}
