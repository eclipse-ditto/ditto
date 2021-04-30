/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link DeleteThing} command.
 */
@Immutable
final class DeleteThingStrategy extends AbstractThingCommandStrategy<DeleteThing> {

    /**
     * Constructs a new {@code DeleteThingStrategy} object.
     */
    DeleteThingStrategy() {
        super(DeleteThing.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final DeleteThing command,
            @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        context.getLog().withCorrelationId(command)
                .info("Deleted Thing with ID <{}>.", thingId);

        final ThingEvent<?> event = ThingDeleted.of(thingId, nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response =
                appendETagHeaderIfProvided(command, DeleteThingResponse.of(thingId, dittoHeaders), null);

        return ResultFactory.newMutationResult(command, event, response, false, true);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteThing command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteThing command, @Nullable final Thing newEntity) {
        return Optional.empty();
    }
}
