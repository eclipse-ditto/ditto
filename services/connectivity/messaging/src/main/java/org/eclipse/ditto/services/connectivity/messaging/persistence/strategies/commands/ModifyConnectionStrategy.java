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

import static org.eclipse.ditto.model.connectivity.ConnectionLifecycle.ACTIVE;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.CLOSE_CONNECTION;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.OPEN_CONNECTION;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.PERSIST_AND_APPLY_EVENT;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.SEND_RESPONSE;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.STOP_CLIENT_ACTORS;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.UPDATE_SUBSCRIPTIONS;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newErrorResult;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newMutationResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnectionResponse;
import org.eclipse.ditto.signals.events.connectivity.ConnectionModified;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection} command.
 */
final class ModifyConnectionStrategy extends AbstractConnectivityCommandStrategy<ModifyConnection> {

    ModifyConnectionStrategy() {
        super(ModifyConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity, final long nextRevision, final ModifyConnection command) {
        final Connection connection = command.getConnection().toBuilder().lifecycle(ACTIVE).build();
        if (entity != null && entity.getConnectionType() != connection.getConnectionType()) {
            return ResultFactory.newErrorResult(
                    ConnectionConfigurationInvalidException
                            .newBuilder("ConnectionType <" + connection.getConnectionType().getName() +
                                    "> of existing connection <" + context.getState().id() + "> cannot be changed!")
                            .dittoHeaders(command.getDittoHeaders())
                            .build()
            );
        }
        final ConnectivityEvent event =
                ConnectionModified.of(connection, getEventTimestamp(), command.getDittoHeaders());
        final WithDittoHeaders response =
                ModifyConnectionResponse.of(context.getState().id(), command.getDittoHeaders());
        final boolean isCurrentConnectionOpen = Optional.ofNullable(entity)
                .map(c -> c.getConnectionStatus() == ConnectivityStatus.OPEN)
                .orElse(false);
        final boolean isNextConnectionOpen = connection.getConnectionStatus() == ConnectivityStatus.OPEN;
        final Optional<DittoRuntimeException> validationError = validate(context, command);
        if (validationError.isPresent()) {
            return newErrorResult(validationError.get());
        } else if (isNextConnectionOpen || isCurrentConnectionOpen) {
            final List<ConnectionAction> actions;
            if (isNextConnectionOpen) {
                context.getLog().debug("Desired connection state is OPEN");
                actions = Arrays.asList(PERSIST_AND_APPLY_EVENT, CLOSE_CONNECTION, STOP_CLIENT_ACTORS,
                        OPEN_CONNECTION, UPDATE_SUBSCRIPTIONS, SEND_RESPONSE);
            } else {
                context.getLog().debug("Desired connection state is not OPEN");
                actions = Arrays.asList(PERSIST_AND_APPLY_EVENT, UPDATE_SUBSCRIPTIONS, CLOSE_CONNECTION,
                        STOP_CLIENT_ACTORS, SEND_RESPONSE);
            }
            return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
        } else {
            return newMutationResult(command, event, response);
        }
    }
}
