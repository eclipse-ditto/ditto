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

import static org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction.ENABLE_LOGGING;
import static org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction.OPEN_CONNECTION;
import static org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction.PERSIST_AND_APPLY_EVENT;
import static org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction.SEND_RESPONSE;
import static org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction.UPDATE_SUBSCRIPTIONS;
import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newErrorResult;
import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newMutationResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionOpened;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;

/**
 * This strategy handles the {@link OpenConnection} command.
 */
final class OpenConnectionStrategy extends AbstractConnectivityCommandStrategy<OpenConnection> {

    OpenConnectionStrategy() {
        super(OpenConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection connection,
            final long nextRevision,
            final OpenConnection command,
            @Nullable final Metadata metadata) {

        final Optional<DittoRuntimeException> validationError = validate(context, command, connection);
        if (validationError.isPresent()) {
            return newErrorResult(validationError.get(), command);
        } else {
            final ConnectivityEvent<?> event = ConnectionOpened.of(context.getState().id(), nextRevision,
                    getEventTimestamp(), command.getDittoHeaders(), metadata);
            final WithDittoHeaders response =
                    OpenConnectionResponse.of(context.getState().id(), command.getDittoHeaders());
            final List<ConnectionAction> actions =
                    Arrays.asList(ENABLE_LOGGING, PERSIST_AND_APPLY_EVENT, OPEN_CONNECTION, UPDATE_SUBSCRIPTIONS,
                            SEND_RESPONSE);
            return newMutationResult(StagedCommand.of(command, event, response, actions), event, response);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final OpenConnection command,
            @Nullable final Connection previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final OpenConnection command, @Nullable final Connection newEntity) {
        return Optional.of(getEntityOrThrow(newEntity)).flatMap(EntityTag::fromEntity);
    }
}
