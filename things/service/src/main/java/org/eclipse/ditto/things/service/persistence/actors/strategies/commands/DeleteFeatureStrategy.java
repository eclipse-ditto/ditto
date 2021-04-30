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
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureResponse;
import org.eclipse.ditto.things.model.signals.events.FeatureDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeature} command.
 */
@Immutable
final class DeleteFeatureStrategy extends AbstractThingCommandStrategy<DeleteFeature> {

    /**
     * Constructs a new {@code DeleteFeatureStrategy} object.
     */
    DeleteFeatureStrategy() {
        super(DeleteFeature.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final DeleteFeature command,
            @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();

        return extractFeature(command, thing)
                .map(feature -> getDeleteFeatureResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), featureId,
                                command.getDittoHeaders()), command));
    }

    private Optional<Feature> extractFeature(final DeleteFeature command, @Nullable final Thing thing) {
        final String featureId = command.getFeatureId();

        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(featureId));
    }

    private Result<ThingEvent<?>> getDeleteFeatureResult(final Context<ThingId> context, final long nextRevision,
            final DeleteFeature command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final ThingEvent<?> event =
                FeatureDeleted.of(thingId, featureId, nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeleteFeatureResponse.of(thingId, featureId, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteFeature command, @Nullable final Thing previousEntity) {
        return extractFeature(command, previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteFeature command, @Nullable final Thing newEntity) {
        return Optional.empty();
    }
}
