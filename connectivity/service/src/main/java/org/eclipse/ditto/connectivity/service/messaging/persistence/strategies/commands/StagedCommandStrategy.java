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

import java.text.MessageFormat;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityInternalErrorException;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;

/**
 * This strategy handles the {@link org.eclipse.ditto.connectivity.service.messaging.persistence.stages.StagedCommand}
 * command.
 */
final class StagedCommandStrategy extends AbstractConnectivityCommandStrategy<StagedCommand> {

    StagedCommandStrategy() {
        super(StagedCommand.class);
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final StagedCommand command,
            @Nullable final Metadata metadata) {

        return command.getEvent().<Result<ConnectivityEvent<?>>>map(
                connectivityEvent -> ResultFactory.newMutationResult(command, connectivityEvent, command.getResponse()))
                .orElseGet(() -> ResultFactory.newErrorResult(ConnectivityInternalErrorException.newBuilder()
                        .message(MessageFormat.format("Staged command <{0}> did not contain required event.", command))
                        .description("This is an internal error. Please contact the service team.")
                        .dittoHeaders(command.getDittoHeaders())
                        .build(), command));
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final StagedCommand command,
            @Nullable final Connection previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final StagedCommand command, @Nullable final Connection newEntity) {
        return Optional.empty();
    }
}
