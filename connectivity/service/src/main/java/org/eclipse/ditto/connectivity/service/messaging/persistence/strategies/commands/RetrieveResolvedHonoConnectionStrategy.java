/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.connectivity.model.signals.commands.query.ConnectivityQueryCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveResolvedHonoConnection;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.hono.HonoConnectionFactory;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonObject;

import akka.actor.ActorSystem;

/**
 * This strategy handles the {@link RetrieveResolvedHonoConnection} command.
 */
final class RetrieveResolvedHonoConnectionStrategy
        extends AbstractConnectivityCommandStrategy<RetrieveResolvedHonoConnection> {

    private final HonoConnectionFactory honoConnectionFactory;

    RetrieveResolvedHonoConnectionStrategy(final ActorSystem actorSystem) {
        super(RetrieveResolvedHonoConnection.class);
        this.honoConnectionFactory = HonoConnectionFactory.get(actorSystem, actorSystem.settings().config());
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final RetrieveResolvedHonoConnection command,
            @Nullable final Metadata metadata) {

        final Result<ConnectivityEvent<?>> result;
        if (entity != null && entity.getConnectionType() == ConnectionType.HONO) {
            return ResultFactory.newQueryResult(command,
                    appendETagHeaderIfProvided(command, getRetrieveConnectionResponse(entity, command), entity)
            );
        } else {
            result = ResultFactory.newErrorResult(notAccessible(context, command), command);
        }
        return result;
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveResolvedHonoConnection command,
            @Nullable final Connection previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveResolvedHonoConnection command,
            @Nullable final Connection newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }

    private DittoHeadersSettable<?> getRetrieveConnectionResponse(@Nullable final Connection connection,
            final ConnectivityQueryCommand<RetrieveResolvedHonoConnection> command) {
        if (connection != null) {
            return RetrieveConnectionResponse.of(getConnectionJson(connection, command),
                    command.getDittoHeaders());
        } else {
            return ConnectionNotAccessibleException.newBuilder(((RetrieveResolvedHonoConnection) command).getEntityId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }
    }

    private JsonObject getConnectionJson(final Connection connection,
            final ConnectivityQueryCommand<RetrieveResolvedHonoConnection> command) {

        final Connection honoConnection = honoConnectionFactory.getHonoConnection(connection);
        return ((RetrieveResolvedHonoConnection) command).getSelectedFields()
                .map(selectedFields -> honoConnection.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> honoConnection.toJson(command.getImplementedSchemaVersion()));
    }
}
