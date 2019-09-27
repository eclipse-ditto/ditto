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

import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.CLOSE_CONNECTION;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.PERSIST_AND_APPLY_EVENT;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.SEND_RESPONSE;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.STOP_CLIENT_ACTORS;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.UPDATE_SUBSCRIPTIONS;
import static org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory.newMutationResult;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.events.connectivity.ConnectionClosed;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection} command.
 */
final class CloseConnectionStrategy extends AbstractConnectivityCommandStrategy<CloseConnection> {

    CloseConnectionStrategy() {
        super(CloseConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
            @Nullable final Connection connection, final long nextRevision, final CloseConnection command) {
        final ConnectivityEvent event = ConnectionClosed.of(context.getState().id(), command.getDittoHeaders());
        final WithDittoHeaders response =
                CloseConnectionResponse.of(context.getState().id(), command.getDittoHeaders());
        final List<ConnectionAction> actions =
                Arrays.asList(PERSIST_AND_APPLY_EVENT, UPDATE_SUBSCRIPTIONS, CLOSE_CONNECTION, STOP_CLIENT_ACTORS,
                        SEND_RESPONSE);
        return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
    }
}
