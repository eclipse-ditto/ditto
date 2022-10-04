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

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveHonoConnection;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.hono.HonoConnectionFactory;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;

/**
 * This strategy handles the {@link RetrieveConnection} command.
 */
final class RetrieveHonoConnectionStrategy extends AbstractConnectivityCommandStrategy<RetrieveHonoConnection> {

    RetrieveHonoConnectionStrategy() {
        super(RetrieveHonoConnection.class);
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final RetrieveHonoConnection command,
            @Nullable final Metadata metadata) {

        final Result<ConnectivityEvent<?>> result;
        if (entity != null && entity.getConnectionType() == ConnectionType.HONO) {
            final var actorSystem = context.getActorSystem();
            final var honoConnectionFactory = HonoConnectionFactory.get(
                    actorSystem, actorSystem.settings().config());
            final var json = honoConnectionFactory.getHonoConnection(entity).toJson();

            result = ResultFactory.newQueryResult(command,
                    RetrieveConnectionResponse.of(json, command.getDittoHeaders()));
        } else {
            result = ResultFactory.newErrorResult(notAccessible(context, command), command);
        }
        return result;
    }
}
