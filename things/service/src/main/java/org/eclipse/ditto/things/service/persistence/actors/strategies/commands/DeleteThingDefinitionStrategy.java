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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingDefinitionNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingDefinition} command.
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
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final DeleteThingDefinition command,
            @Nullable final Metadata metadata) {

        return extractDefinition(thing)
                .map(definition -> getDeleteDefinitionResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ThingDefinitionNotAccessibleException.newBuilder(context.getState())
                                .dittoHeaders(command.getDittoHeaders())
                                .build(), command));
    }

    private Optional<ThingDefinition> extractDefinition(final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getDefinition();
    }

    private Result<ThingEvent<?>> getDeleteDefinitionResult(final Context<ThingId> context, final long nextRevision,
            final DeleteThingDefinition command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeleteThingDefinitionResponse.of(thingId, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command,
                ThingDefinitionDeleted.of(thingId, nextRevision, getEventTimestamp(), dittoHeaders, metadata),
                response);
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
