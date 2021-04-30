/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredPropertyResponse;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertyDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredProperty} command.
 */
@Immutable
final class DeleteFeatureDesiredPropertyStrategy extends AbstractThingCommandStrategy<DeleteFeatureDesiredProperty> {

    /**
     * Constructs a new {@code DeleteFeatureDesiredPropertyStrategy} object.
     */
    DeleteFeatureDesiredPropertyStrategy() {
        super(DeleteFeatureDesiredProperty.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final DeleteFeatureDesiredProperty command,
            @Nullable final Metadata metadata) {

        return extractFeature(command, thing)
                .map(feature -> getDeleteFeatureDesiredPropertyResult(feature, context, nextRevision, command, thing,
                        metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), command.getFeatureId(),
                                command.getDittoHeaders()), command));
    }

    private Optional<Feature> extractFeature(final DeleteFeatureDesiredProperty command, final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getDeleteFeatureDesiredPropertyResult(final Feature feature,
            final Context<ThingId> context,
            final long nextRevision,
            final DeleteFeatureDesiredProperty command,
            @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        final JsonPointer desiredPropertyPointer = command.getDesiredPropertyPointer();
        final ThingId thingId = context.getState();
        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return feature.getDesiredProperties()
                .flatMap(desiredProperties -> desiredProperties.getValue(desiredPropertyPointer))
                .<Result<ThingEvent<?>>>map(featureDesiredProperty -> {
                    final ThingEvent<?> event =
                            FeatureDesiredPropertyDeleted.of(thingId, featureId, desiredPropertyPointer, nextRevision,
                                    getEventTimestamp(), dittoHeaders, metadata);
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            DeleteFeatureDesiredPropertyResponse.of(thingId, featureId, desiredPropertyPointer,
                                    dittoHeaders), thing);
                    return ResultFactory.newMutationResult(command, event, response);
                })
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureDesiredPropertyNotFound(thingId, featureId, desiredPropertyPointer,
                                dittoHeaders),
                        command));
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteFeatureDesiredProperty command,
            @Nullable final Thing previousEntity) {

        return extractFeature(command, previousEntity).flatMap(Feature::getDesiredProperties)
                .flatMap(properties -> properties.getValue(command.getDesiredPropertyPointer()))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteFeatureDesiredProperty command,
            @Nullable final Thing newEntity) {

        return Optional.empty();
    }
}
