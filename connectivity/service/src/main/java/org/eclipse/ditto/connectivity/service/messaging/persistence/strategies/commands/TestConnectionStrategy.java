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

import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newErrorResult;
import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newMutationResult;
import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newQueryResult;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionCreated;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;

/**
 * This strategy handles the {@link org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection} command.
 */
final class TestConnectionStrategy extends AbstractConnectivityCommandStrategy<TestConnection> {

    TestConnectionStrategy() {
        super(TestConnection.class);
    }

    @Override
    public boolean isDefined(final TestConnection command) {
        return true;
    }

    @Override
    public boolean isDefined(final Context<ConnectionState> context, @Nullable final Connection connection, final TestConnection command) {
        final boolean connectionExists = Optional.ofNullable(connection)
                .map(t -> !t.isDeleted())
                .orElse(false);

        return !connectionExists && Objects.equals(context.getState().id(), command.getEntityId());
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final TestConnection command,
            @Nullable final Metadata metadata) {

        final Optional<DittoRuntimeException> validationError = validate(context, command);
        if (validationError.isPresent()) {
            return newErrorResult(validationError.get(), command);
        } else if (entity == null) {
            final Connection connection = command.getConnection();
            final ConnectivityEvent<?> event = ConnectionCreated.of(connection, nextRevision,
                    getEventTimestamp(), command.getDittoHeaders(), metadata);
            final List<ConnectionAction> actions =
                    Arrays.asList(
                            ConnectionAction.APPLY_EVENT, ConnectionAction.TEST_CONNECTION, ConnectionAction.SEND_RESPONSE, ConnectionAction.PASSIVATE);
            final StagedCommand stagedCommand = StagedCommand.of(command, event, command, actions);
            return newMutationResult(stagedCommand, event, command);
        } else {
            return newQueryResult(command,
                    TestConnectionResponse.alreadyCreated(context.getState().id(), command.getDittoHeaders()));
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final TestConnection command,
            @Nullable final Connection previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final TestConnection command, @Nullable final Connection newEntity) {
        return Optional.empty();
    }
}
