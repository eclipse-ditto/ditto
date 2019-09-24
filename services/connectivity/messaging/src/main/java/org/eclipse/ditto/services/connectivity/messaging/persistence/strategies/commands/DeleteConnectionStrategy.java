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

import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.BECOME_DELETED;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.DISABLE_LOGGING;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.PERSIST_AND_APPLY_EVENT;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.SEND_RESPONSE;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.STOP_CLIENT_ACTORS;
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
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.events.connectivity.ConnectionDeleted;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection} command.
 */
final class DeleteConnectionStrategy extends AbstractConnectivityCommandStrategy<DeleteConnection> {

    DeleteConnectionStrategy() {
        super(DeleteConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent> doApply(final Context<ConnectionState> context,
            @Nullable final Connection connection, final long nextRevision, final DeleteConnection command) {
        final ConnectivityEvent event = ConnectionDeleted.of(context.getState().id(), command.getDittoHeaders());
        final WithDittoHeaders response =
                DeleteConnectionResponse.of(context.getState().id(), command.getDittoHeaders());
        // Not closing the connection asynchronously; rely on client actors to cleanup all resources when stopped.
        final List<ConnectionAction> actions =
                Arrays.asList(PERSIST_AND_APPLY_EVENT, STOP_CLIENT_ACTORS, SEND_RESPONSE, DISABLE_LOGGING,
                        BECOME_DELETED);
        return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
    }
}
