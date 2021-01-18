/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.persistence.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractCommandStrategy;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

/**
 * Abstract base class for {@link org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand} strategies.
 *
 * @param <C> the type of the handled command
 */
abstract class AbstractConnectivityCommandStrategy<C extends ConnectivityCommand<?>>
        extends AbstractCommandStrategy<C, Connection, ConnectionState, ConnectivityEvent<?>> {

    AbstractConnectivityCommandStrategy(final Class<?> theMatchingClass) {
        super(theMatchingClass);
    }

    @Override
    public boolean isDefined(final C command) {
        return true;
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final Connection entity,
            final C command) {
        return Optional.empty();
    }

    ConnectionNotAccessibleException notAccessible(final Context<ConnectionState> context,
            final WithDittoHeaders<?> command) {
        return ConnectionNotAccessibleException.newBuilder(context.getState().id())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    static Optional<DittoRuntimeException> validate(final Context<ConnectionState> context,
            final ConnectivityCommand<?> command, final Connection connection) {
        try {
            context.getState().getValidator().accept(command, () -> connection);
            return Optional.empty();
        } catch (final Exception error) {
            return handleValidationException(context, command, error);
        }
    }

    static Optional<DittoRuntimeException> validate(final Context<ConnectionState> context,
            final ConnectivityCommand<?> command) {
        try {
            context.getState().getValidator().accept(command);
            return Optional.empty();
        } catch (final Exception error) {
            return handleValidationException(context, command, error);
        }
    }

    private static Optional<DittoRuntimeException> handleValidationException(
            final Context<ConnectionState> context, final ConnectivityCommand<?> command,
            final Exception error) {
        final DittoRuntimeException dre =
                toDittoRuntimeException(error, context.getState().id(), command.getDittoHeaders());
        context.getLog().withCorrelationId(dre)
                .info("Operation <{}> failed due to <{}>", command, dre);
        context.getState()
                .getConnectionLogger()
                .failure("Operation {0} failed due to {1}", command.getType(), dre.getMessage());
        return Optional.of(dre);
    }

    private static DittoRuntimeException toDittoRuntimeException(final Throwable error, final ConnectionId id,
            final DittoHeaders headers) {
        return DittoRuntimeException.asDittoRuntimeException(error,
                cause -> ConnectionFailedException.newBuilder(id)
                        .description(cause.getMessage())
                        .cause(cause)
                        .dittoHeaders(headers)
                        .build());
    }

}
