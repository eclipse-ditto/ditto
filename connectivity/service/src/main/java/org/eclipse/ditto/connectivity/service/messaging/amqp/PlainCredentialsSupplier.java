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
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;

import akka.http.javadsl.model.Uri;

/**
 * Supplier of optional username-password credentials.
 */
@FunctionalInterface
public interface PlainCredentialsSupplier {

    /**
     * Get the username-password credentials of a connection.
     *
     * @param connection the connection.
     * @return the optional credentials.
     */
    Optional<UserPasswordCredentials> get(Connection connection);

    /**
     * Remove userinfo from a connection URI.
     *
     * @param uri the URI.
     * @return the URI without userinfo.
     */
    default String getUriWithoutUserinfo(final String uri) {
        return Uri.create(uri).userInfo("").toString();
    }

    /**
     * Create a {@code PlainCredentialsSupplier} from the URI of the connection.
     *
     * @return the URI.
     */
    static PlainCredentialsSupplier fromUri() {
        return (connection) -> connection.getUsername().flatMap(username ->
                connection.getPassword()
                        .map(password -> UserPasswordCredentials.newInstance(username, password)));
    }

}
