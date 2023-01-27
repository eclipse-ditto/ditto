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
import static org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction.BECOME_CREATED;
import static org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction.ENABLE_LOGGING;
import static org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction.OPEN_CONNECTION_IGNORE_ERRORS;
import static org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction.PERSIST_AND_APPLY_EVENT;
import static org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction.SEND_RESPONSE;
import static org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction.UPDATE_SUBSCRIPTIONS;
import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newErrorResult;
import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newMutationResult;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionCreated;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;

/**
 * This strategy handles the {@link CreateConnection} command.
 */
final class CreateConnectionStrategy extends AbstractConnectivityCommandStrategy<CreateConnection> {

    CreateConnectionStrategy() {
        super(CreateConnection.class);
    }

    @Override
    public boolean isDefined(final CreateConnection command) {
        return true;
    }

    @Override
    public boolean isDefined(final Context<ConnectionState> context, @Nullable final Connection connection,
            final CreateConnection command) {
        final boolean connectionExists = Optional.ofNullable(connection)
                .map(t -> !t.isDeleted())
                .orElse(false);

        return !connectionExists && Objects.equals(context.getState().id(), command.getEntityId());
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final CreateConnection command,
            @Nullable final Metadata metadata) {

        final Instant timestamp = getEventTimestamp();
        final Connection connection = command.getConnection().toBuilder().lifecycle(ACTIVE)
                .revision(nextRevision)
                .created(timestamp)
                .modified(timestamp)
                .build();
        final ConnectivityEvent<?> event =
                ConnectionCreated.of(connection, nextRevision, getEventTimestamp(), command.getDittoHeaders(),
                        metadata);
        final WithDittoHeaders response =
                CreateConnectionResponse.of(connection, command.getDittoHeaders());
        final Optional<DittoRuntimeException> validationError = validate(context, command);
        if (validationError.isPresent()) {
            return newErrorResult(validationError.get(), command);
        } else if (connection.getConnectionStatus() == ConnectivityStatus.OPEN) {
            context.getLog().withCorrelationId(command)
                    .debug("Connection <{}> has status <{}> and will therefore be opened.",
                            connection.getId(), connection.getConnectionStatus());
            final List<ConnectionAction> actions =
                    List.of(ENABLE_LOGGING, PERSIST_AND_APPLY_EVENT, BECOME_CREATED, UPDATE_SUBSCRIPTIONS,
                            SEND_RESPONSE, OPEN_CONNECTION_IGNORE_ERRORS);
            return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
        } else {
            return newMutationResult(command, event, response, true, false);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final CreateConnection command,
            @Nullable final Connection previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final CreateConnection command, @Nullable final Connection newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }
}
