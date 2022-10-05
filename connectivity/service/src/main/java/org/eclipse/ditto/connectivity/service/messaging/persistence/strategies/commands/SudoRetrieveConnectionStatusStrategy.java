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
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionStatus;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionStatusResponse;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionLifecycle;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;

/**
 * This strategy handles the {@link org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionStatus}
 * command.
 */
final class SudoRetrieveConnectionStatusStrategy
        extends AbstractConnectivityCommandStrategy<SudoRetrieveConnectionStatus> {

    SudoRetrieveConnectionStatusStrategy() {
        super(SudoRetrieveConnectionStatus.class);
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final SudoRetrieveConnectionStatus command,
            @Nullable final Metadata metadata) {

        final var connectionStatus = entity != null && !entity.hasLifecycle(ConnectionLifecycle.DELETED)
                ? entity.getConnectionStatus()
                : ConnectivityStatus.CLOSED;
        final var clientCount = entity != null ? entity.getClientCount() : 0;
        return ResultFactory.newQueryResult(command,
                SudoRetrieveConnectionStatusResponse.of(connectionStatus, clientCount, command.getDittoHeaders()));
    }
}
