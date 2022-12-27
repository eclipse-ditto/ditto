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

import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newMutationResult;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionClosed;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection} command.
 */
final class CloseConnectionStrategy extends AbstractConnectivityCommandStrategy<CloseConnection> {

    CloseConnectionStrategy() {
        super(CloseConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection connection,
            final long nextRevision,
            final CloseConnection command,
            @Nullable final Metadata metadata) {

        final ConnectivityEvent<?> event = ConnectionClosed.of(context.getState().id(), nextRevision,
                getEventTimestamp(), command.getDittoHeaders(), metadata);
        final WithDittoHeaders response =
                CloseConnectionResponse.of(context.getState().id(), command.getDittoHeaders());
        final List<ConnectionAction> actions =
                Arrays.asList(ConnectionAction.PERSIST_AND_APPLY_EVENT, ConnectionAction.CHECK_LOGGING_ENABLED,
                        ConnectionAction.STOP_CLIENT_ACTORS, ConnectionAction.SEND_RESPONSE);
        return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
    }
}
