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

import static org.eclipse.ditto.connectivity.model.ConnectionLifecycle.ACTIVE;
import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newErrorResult;
import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newMutationResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionModified;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnection} command.
 */
final class ModifyConnectionStrategy extends AbstractConnectivityCommandStrategy<ModifyConnection> {

    ModifyConnectionStrategy() {
        super(ModifyConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final ModifyConnection command,
            @Nullable final Metadata metadata) {

        final Connection connection = command.getConnection().toBuilder().lifecycle(ACTIVE).build();
        if (entity != null && entity.getConnectionType() != connection.getConnectionType() &&
                !command.getDittoHeaders().isSudo()) {
            return ResultFactory.newErrorResult(
                    ConnectionConfigurationInvalidException
                            .newBuilder("ConnectionType <" + connection.getConnectionType().getName() +
                                    "> of existing connection <" + context.getState().id() + "> cannot be changed!")
                            .dittoHeaders(command.getDittoHeaders())
                            .build(),
                    command
            );
        }
        final ConnectivityEvent<?> event =
                ConnectionModified.of(connection, nextRevision, getEventTimestamp(), command.getDittoHeaders(),
                        metadata);
        final WithDittoHeaders response =
                ModifyConnectionResponse.of(context.getState().id(), command.getDittoHeaders());
        final boolean isCurrentConnectionOpen = Optional.ofNullable(entity)
                .map(c -> c.getConnectionStatus() == ConnectivityStatus.OPEN)
                .orElse(false);
        final boolean isNextConnectionOpen = connection.getConnectionStatus() == ConnectivityStatus.OPEN;
        final Optional<DittoRuntimeException> validationError = validate(context, command);
        if (validationError.isPresent()) {
            return newErrorResult(validationError.get(), command);
        } else if (isNextConnectionOpen || isCurrentConnectionOpen) {
            final List<ConnectionAction> actions;
            if (isNextConnectionOpen) {
                context.getLog().withCorrelationId(command)
                        .debug("Desired connection state is OPEN");
                actions = Arrays.asList(ConnectionAction.PERSIST_AND_APPLY_EVENT, ConnectionAction.CLOSE_CONNECTION,
                        ConnectionAction.OPEN_CONNECTION, ConnectionAction.CHECK_LOGGING_ENABLED,
                        ConnectionAction.SEND_RESPONSE);
            } else {
                context.getLog().withCorrelationId(command)
                        .debug("Desired connection state is not OPEN");
                actions = Arrays.asList(ConnectionAction.PERSIST_AND_APPLY_EVENT,
                        ConnectionAction.CHECK_LOGGING_ENABLED, ConnectionAction.CLOSE_CONNECTION,
                        ConnectionAction.STOP_CLIENT_ACTORS, ConnectionAction.SEND_RESPONSE);
            }
            return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
        } else {
            return newMutationResult(command, event, response);
        }
    }
}
