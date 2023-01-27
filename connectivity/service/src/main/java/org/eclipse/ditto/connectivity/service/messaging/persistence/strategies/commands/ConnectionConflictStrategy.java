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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionConflictException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;

/**
 * This strategy handles the {@link org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection} command
 * when a conflict was encountered.
 */
final class ConnectionConflictStrategy extends AbstractConnectivityCommandStrategy<CreateConnection> {

    ConnectionConflictStrategy() {
        super(CreateConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final CreateConnection command,
            @Nullable final Metadata metadata) {

        context.getLog().withCorrelationId(command)
                .info("Connection <{}> already exists! Responding with conflict.", context.getState().id());
        final ConnectionConflictException conflictException =
                ConnectionConflictException.newBuilder(context.getState().id())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();
        return newErrorResult(conflictException, command);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final CreateConnection command,
            @Nullable final Connection previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final CreateConnection command, @Nullable final Connection newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }
}
