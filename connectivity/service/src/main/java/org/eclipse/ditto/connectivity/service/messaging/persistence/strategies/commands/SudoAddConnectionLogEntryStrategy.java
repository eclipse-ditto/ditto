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
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoAddConnectionLogEntry;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLoggerRegistry;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;

/**
 * This strategy handles the {@link SudoAddConnectionLogEntry} command.
 */
final class SudoAddConnectionLogEntryStrategy extends AbstractConnectivityCommandStrategy<SudoAddConnectionLogEntry> {

    SudoAddConnectionLogEntryStrategy() {
        super(SudoAddConnectionLogEntry.class);
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final SudoAddConnectionLogEntry command,
            @Nullable final Metadata metadata) {

        final var logEntry = command.getLogEntry();
        context.getLog().withCorrelationId(logEntry.getCorrelationId())
                .debug("Handling <{}>.", command);
        final var logger = getAppropriateLogger(context.getState().getConnectionLoggerRegistry(),
                command.getEntityId(), logEntry);
        logger.logEntry(logEntry);
        return ResultFactory.emptyResult();
    }

    private ConnectionLogger getAppropriateLogger(final ConnectionLoggerRegistry connectionLoggerRegistry,
            final ConnectionId connectionId, final LogEntry logEntry) {
        return connectionLoggerRegistry.getLogger(connectionId,
                logEntry.getLogCategory(),
                logEntry.getLogType(),
                logEntry.getAddress().orElse(null));
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final SudoAddConnectionLogEntry command,
            @Nullable final Connection previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final SudoAddConnectionLogEntry command,
            @Nullable final Connection newEntity) {
        return Optional.empty();
    }
}
