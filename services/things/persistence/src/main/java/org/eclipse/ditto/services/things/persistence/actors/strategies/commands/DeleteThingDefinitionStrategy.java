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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingDefinition;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingDefinitionNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingDefinitionResponse;
import org.eclipse.ditto.signals.events.things.ThingDefinitionDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteThingDefinition} command.
 */
@Immutable
final class DeleteThingDefinitionStrategy
        extends AbstractThingCommandStrategy<DeleteThingDefinition> {

    /**
     * Constructs a new {@code DeleteThingDefinitionStrategy} object.
     */
    DeleteThingDefinitionStrategy() {
        super(DeleteThingDefinition.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final DeleteThingDefinition command) {

        return extractDefinition(thing)
                .map(definition -> getDeleteDefinitionResult(context, nextRevision, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ThingDefinitionNotAccessibleException.newBuilder(context.getState())
                                .dittoHeaders(command.getDittoHeaders())
                                .build()));
    }

    private Optional<ThingDefinition> extractDefinition(final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getDefinition();
    }

    private Result<ThingEvent> getDeleteDefinitionResult(final Context<ThingId> context, final long nextRevision,
            final DeleteThingDefinition command, @Nullable Thing thing) {
        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeleteThingDefinitionResponse.of(thingId, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command,
                ThingDefinitionDeleted.of(thingId, nextRevision, getEventTimestamp(), dittoHeaders), response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteThingDefinition command,
            @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(Thing::getDefinition).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteThingDefinition command, @Nullable final Thing newEntity) {
        return Optional.empty();
    }
}