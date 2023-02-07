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
package org.eclipse.ditto.connectivity.service.messaging.persistence.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.api.commands.sudo.ConnectivitySudoCommand;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.internal.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.internal.utils.persistentactors.etags.AbstractConditionHeaderCheckingCommandStrategy;

/**
 * Abstract base class for {@link org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand} strategies.
 *
 * @param <C> the type of the handled command
 */
abstract class AbstractConnectivityCommandStrategy<C extends Command<?>>
        extends AbstractConditionHeaderCheckingCommandStrategy<C, Connection, ConnectionState, ConnectivityEvent<?>> {

    private static final ConditionalHeadersValidator VALIDATOR =
            ConnectionsConditionalHeadersValidatorProvider.getInstance();

    protected AbstractConnectivityCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    @Override
    protected ConditionalHeadersValidator getValidator() {
        return VALIDATOR;
    }

    @Override
    public boolean isDefined(final C command) {
        return command instanceof ConnectivityCommand || command instanceof ConnectivitySudoCommand;
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable final Connection entity,
            final C command) {
        return Optional.empty();
    }

    ConnectionNotAccessibleException notAccessible(final Context<ConnectionState> context,
            final WithDittoHeaders command) {
        return ConnectionNotAccessibleException.newBuilder(context.getState().id())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    static Optional<DittoRuntimeException> validate(final Context<ConnectionState> context,
            final ConnectivityCommand<?> command, @Nullable final Connection connection) {
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
