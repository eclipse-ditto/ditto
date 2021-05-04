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

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnection} command.
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
                    RetrieveConnectionResponse.of(entity.toJson(), command.getDittoHeaders()));
        } else {
            return ResultFactory.newErrorResult(notAccessible(context, command), command);
        }
    }
}
