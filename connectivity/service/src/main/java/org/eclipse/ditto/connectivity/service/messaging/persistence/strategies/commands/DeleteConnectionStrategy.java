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
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.DeleteConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.DeleteConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionDeleted;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;

/**
 * This strategy handles the {@link DeleteConnection}
 * command.
 */
final class DeleteConnectionStrategy extends AbstractConnectivityCommandStrategy<DeleteConnection> {

    DeleteConnectionStrategy() {
        super(DeleteConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection connection,
            final long nextRevision,
            final DeleteConnection command,
            @Nullable final Metadata metadata) {

        final ConnectivityEvent<?> event = ConnectionDeleted.of(context.getState().id(), nextRevision,
                getEventTimestamp(), command.getDittoHeaders(), metadata);
        final WithDittoHeaders response =
                DeleteConnectionResponse.of(context.getState().id(), command.getDittoHeaders());
        // Not closing the connection asynchronously; rely on client actors to cleanup all resources when stopped.
        final List<ConnectionAction> actions =
                Arrays.asList(ConnectionAction.PERSIST_AND_APPLY_EVENT, ConnectionAction.UPDATE_SUBSCRIPTIONS,
                        ConnectionAction.CLOSE_CONNECTION, ConnectionAction.STOP_CLIENT_ACTORS,
                        ConnectionAction.DISABLE_LOGGING, ConnectionAction.SEND_RESPONSE,
                        ConnectionAction.BECOME_DELETED);
        return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteConnection command,
            @Nullable final Connection previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteConnection command, @Nullable final Connection newEntity) {
        return Optional.empty();
    }
}
