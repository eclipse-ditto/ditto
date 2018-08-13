/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.validation;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectionUriInvalidException;

/**
 * Protocol-specific specification for {@link org.eclipse.ditto.model.connectivity.Connection} objects.
 */
public interface ProtocolValidator {

    /**
     * Type of connection for which this spec applies.
     *
     * @return the connection type.
     */
    ConnectionType type();

    /**
     * Check a connection of the declared type for errors and throw them if any exists.
     *
     * @param connection the connection to check for errors.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     * @throws DittoRuntimeException if the connection has errors.
     */
    void validate(final Connection connection, final DittoHeaders dittoHeaders) throws DittoRuntimeException;

    /**
     * Check whether the URI scheme of the connection belongs to an accepted scheme.
     *
     * @param connection the connection to check.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     * @param acceptedSchemes valid URI schemes for the connection type.
     * @param protocolName protocol name of the connection type.
     * @throws DittoRuntimeException if the URI scheme is not accepted.
     */
    static void validateUriScheme(final Connection connection,
            final DittoHeaders dittoHeaders,
            final Collection<String> acceptedSchemes,
            final String protocolName) throws DittoRuntimeException {

        if (!acceptedSchemes.contains(connection.getProtocol())) {
            final String message =
                    MessageFormat.format("The URI scheme ''{0}'' is not valid for {1}.", connection.getProtocol(),
                            protocolName);
            final String description =
                    MessageFormat.format("Accepted URI schemes are: {0}",
                            acceptedSchemes.stream().collect(Collectors.joining(", ")));
            throw ConnectionUriInvalidException.newBuilder(connection.getUri())
                    .message(message)
                    .description(description)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }
}
