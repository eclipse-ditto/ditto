/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.validation;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.function.Supplier;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectionUriInvalidException;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;

/**
 * Protocol-specific specification for {@link org.eclipse.ditto.model.connectivity.Connection} objects.
 */
public abstract class AbstractProtocolValidator {

    /**
     * Type of connection for which this spec applies.
     *
     * @return the connection type.
     */
    public abstract ConnectionType type();

    /**
     * Check a connection of the declared type for errors and throw them if any exists.
     *
     * @param connection the connection to check for errors.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     * @throws DittoRuntimeException if the connection has errors.
     */
    public abstract void validate(final Connection connection, final DittoHeaders dittoHeaders);

    /**
     * Check whether the URI scheme of the connection belongs to an accepted scheme.
     *
     * @param connection the connection to check.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     * @param acceptedSchemes valid URI schemes for the connection type.
     * @param protocolName protocol name of the connection type.
     * @throws DittoRuntimeException if the URI scheme is not accepted.
     */
    protected static void validateUriScheme(final Connection connection,
            final DittoHeaders dittoHeaders,
            final Collection<String> acceptedSchemes,
            final String protocolName) {

        if (!acceptedSchemes.contains(connection.getProtocol())) {
            final String message =
                    MessageFormat.format("The URI scheme ''{0}'' is not valid for {1}.", connection.getProtocol(),
                            protocolName);
            final String description =
                    MessageFormat.format("Accepted URI schemes are: {0}", String.join(", ", acceptedSchemes));
            throw ConnectionUriInvalidException.newBuilder(connection.getUri())
                    .message(message)
                    .description(description)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    /**
     * Validate protocol-specific configurations of sources.
     *
     * @param connection the connection to check.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     */
    protected void validateSourceConfigs(final Connection connection, final DittoHeaders dittoHeaders) {
        connection.getSources().forEach(source ->
                validateSource(source, dittoHeaders, sourceDescription(source, connection)));
    }

    protected abstract void validateSource(final Source source, final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription);

    /**
     * Validate protocol-specific configurations of targets.
     *
     * @param connection the connection to check.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     */
    protected void validateTargetConfigs(final Connection connection,
            final DittoHeaders dittoHeaders) {
        connection.getTargets().forEach(target ->
                validateTarget(target, dittoHeaders, targetDescription(target, connection)));
    }

    protected abstract void validateTarget(final Target target, final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription);

    /**
     * Obtain a supplier of a description of a source of a connection.
     *
     * @param source the source.
     * @param connection the connection.
     * @return supplier of the description.
     */
    private static Supplier<String> sourceDescription(final Source source, final Connection connection) {
        return () -> MessageFormat.format("Source of index {0} of connection ''{1}''",
                source.getIndex(), connection.getId());
    }

    /**
     * Obtain a supplier of a description of a target of a connection.
     *
     * @param target the target.
     * @param connection the connection.
     * @return supplier of the description.
     */
    private static Supplier<String> targetDescription(final Target target, final Connection connection) {
        return () -> MessageFormat.format("Target of address ''{0}'' of connection ''{1}''",
                target.getAddress(), connection.getId());
    }
}
