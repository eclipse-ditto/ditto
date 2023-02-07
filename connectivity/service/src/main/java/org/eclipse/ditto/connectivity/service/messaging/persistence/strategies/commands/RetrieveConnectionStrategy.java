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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.connectivity.model.signals.commands.query.ConnectivityQueryCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonObject;

/**
 * This strategy handles the {@link RetrieveConnection} command.
 */
final class RetrieveConnectionStrategy extends AbstractConnectivityCommandStrategy<RetrieveConnection> {

    RetrieveConnectionStrategy() {
        super(RetrieveConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final RetrieveConnection command,
            @Nullable final Metadata metadata) {

        if (entity != null) {
            return ResultFactory.newQueryResult(command,
                    appendETagHeaderIfProvided(command, getRetrieveConnectionResponse(entity, command), entity)
            );
        } else {
            return ResultFactory.newErrorResult(notAccessible(context, command), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveConnection command,
            @Nullable final Connection previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveConnection command, @Nullable final Connection newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }

    private static DittoHeadersSettable<?> getRetrieveConnectionResponse(@Nullable final Connection connection,
            final ConnectivityQueryCommand<RetrieveConnection> command) {
        if (connection != null) {
            return RetrieveConnectionResponse.of(getConnectionJson(connection, command),
                    command.getDittoHeaders());
        } else {
            return ConnectionNotAccessibleException.newBuilder(((RetrieveConnection) command).getEntityId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }
    }

    private static JsonObject getConnectionJson(final Connection connection,
            final ConnectivityQueryCommand<RetrieveConnection> command) {
        return ((RetrieveConnection) command).getSelectedFields()
                .map(selectedFields -> connection.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> connection.toJson(command.getImplementedSchemaVersion()));
    }
}
