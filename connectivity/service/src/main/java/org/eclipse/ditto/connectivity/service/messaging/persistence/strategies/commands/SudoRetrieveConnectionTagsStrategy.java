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
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionTags;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoRetrieveConnectionTagsResponse;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;

/**
 * This strategy handles the {@link SudoRetrieveConnectionTags} command.
 */
final class SudoRetrieveConnectionTagsStrategy extends AbstractConnectivityCommandStrategy<SudoRetrieveConnectionTags> {

    SudoRetrieveConnectionTagsStrategy() {
        super(SudoRetrieveConnectionTags.class);
    }

    @Override
    protected Result<ConnectivityEvent<?>> doApply(final Context<ConnectionState> context,
            @Nullable final Connection entity,
            final long nextRevision,
            final SudoRetrieveConnectionTags command,
            @Nullable final Metadata metadata) {

        if (entity != null) {
            return ResultFactory.newQueryResult(command,
                    SudoRetrieveConnectionTagsResponse.of(entity.getTags(), command.getDittoHeaders()));
        } else {
            return ResultFactory.newErrorResult(notAccessible(context, command), command);
        }
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final SudoRetrieveConnectionTags command,
            @Nullable final Connection previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final SudoRetrieveConnectionTags command,
            @Nullable final Connection newEntity) {
        return Optional.empty();
    }
}
