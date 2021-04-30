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
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionCreated;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link ModifyThingDefinition} command.
 */
@Immutable
final class ModifyThingDefinitionStrategy extends AbstractThingCommandStrategy<ModifyThingDefinition> {

    /**
     * Constructs a new {@code ModifyThingDefinitionStrategy} object.
     */
    ModifyThingDefinitionStrategy() {
        super(ModifyThingDefinition.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyThingDefinition command,
            @Nullable final Metadata metadata) {

        return extractDefinition(thing)
                .map(definition -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    private Optional<ThingDefinition> extractDefinition(final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getDefinition();
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyThingDefinition command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                ThingDefinitionModified.of(thingId, command.getDefinition(), nextRevision, getEventTimestamp(),
                        dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyThingDefinitionResponse.modified(thingId, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyThingDefinition command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final ThingDefinition definition = command.getDefinition();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                ThingDefinitionCreated.of(thingId, definition, nextRevision, getEventTimestamp(), dittoHeaders,
                        metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyThingDefinitionResponse.created(thingId, definition, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyThingDefinition command,
            @Nullable final Thing previousEntity) {
        return extractDefinition(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyThingDefinition command, @Nullable final Thing newEntity) {
        return Optional.of(command.getDefinition()).flatMap(EntityTag::fromEntity);
    }
}
