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

import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogs;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionAction;

/**
 * This strategy handles the {@link RetrieveConnectionLogs}
 * command.
 */
final class RetrieveConnectionLogsStrategy extends AbstractSingleActionStrategy<RetrieveConnectionLogs> {

    RetrieveConnectionLogsStrategy() {
        super(RetrieveConnectionLogs.class);
    }

    @Override
    ConnectionAction getAction() {
        return ConnectionAction.RETRIEVE_CONNECTION_LOGS;
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveConnectionLogs command,
            @Nullable final Connection previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveConnectionLogs command,
            @Nullable final Connection newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }
}
