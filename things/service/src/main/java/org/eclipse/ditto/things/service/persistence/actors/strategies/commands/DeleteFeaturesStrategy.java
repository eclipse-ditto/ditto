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
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.things.model.signals.events.FeaturesDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatures} command.
 */
@Immutable
final class DeleteFeaturesStrategy extends AbstractThingCommandStrategy<DeleteFeatures> {

    /**
     * Constructs a new {@code DeleteFeaturesStrategy} object.
     */
    DeleteFeaturesStrategy() {
        super(DeleteFeatures.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final DeleteFeatures command,
            @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return extractFeatures(thing)
                .map(features ->
                        ResultFactory.<ThingEvent<?>>newMutationResult(command,
                                getEventToPersist(context, nextRevision, dittoHeaders, metadata),
                                getResponse(context, command, thing))
                )
                .orElseGet(() ->
                        ResultFactory.newErrorResult(ExceptionFactory.featuresNotFound(context.getState(),
                                dittoHeaders), command));
    }

    private Optional<Features> extractFeatures(final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getFeatures();
    }

    private static ThingEvent<?> getEventToPersist(final Context<ThingId> context, final long nextRevision,
            final DittoHeaders dittoHeaders, @Nullable final Metadata metadata) {

        return FeaturesDeleted.of(context.getState(), nextRevision, getEventTimestamp(), dittoHeaders, metadata);
    }

    private WithDittoHeaders getResponse(final Context<ThingId> context, final DeleteFeatures command,
            @Nullable final Thing thing) {
        return appendETagHeaderIfProvided(command,
                DeleteFeaturesResponse.of(context.getState(), command.getDittoHeaders()), thing);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteFeatures command, @Nullable final Thing previousEntity) {
        return extractFeatures(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteFeatures command, @Nullable final Thing newEntity) {
        return Optional.empty();
    }
}
