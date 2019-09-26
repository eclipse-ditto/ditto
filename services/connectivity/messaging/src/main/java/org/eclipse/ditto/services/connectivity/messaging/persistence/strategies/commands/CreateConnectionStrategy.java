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
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.BECOME_CREATED;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.OPEN_CONNECTION_IGNORE_ERRORS;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.PERSIST_AND_APPLY_EVENT;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.SEND_RESPONSE;
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
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection} command.
 */
final class CreateConnectionStrategy extends AbstractConnectivityCommandStrategy<CreateConnection> {

    CreateConnectionStrategy() {
        super(CreateConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity, final long nextRevision, final CreateConnection command) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(context.getLog(), command,
                context.getState().id());
        final Connection connection = command.getConnection().toBuilder().lifecycle(ACTIVE).build();
        final ConnectivityEvent event =
                ConnectionCreated.of(connection, getEventTimestamp(), command.getDittoHeaders());
        final WithDittoHeaders response =
                CreateConnectionResponse.of(connection, command.getDittoHeaders());
        final Optional<DittoRuntimeException> validationError = validate(context, command);
        if (validationError.isPresent()) {
            return newErrorResult(validationError.get());
        } else if (connection.getConnectionStatus() == ConnectivityStatus.OPEN) {
            context.getLog().debug("Connection <{}> has status <{}> and will therefore be opened.",
                    connection.getId(), connection.getConnectionStatus());
            final List<ConnectionAction> actions = Arrays.asList(
                    PERSIST_AND_APPLY_EVENT, OPEN_CONNECTION_IGNORE_ERRORS, UPDATE_SUBSCRIPTIONS, SEND_RESPONSE,
                    BECOME_CREATED);
            return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
        } else {
            return newMutationResult(command, event, response, true, false);
        }
    }
}
