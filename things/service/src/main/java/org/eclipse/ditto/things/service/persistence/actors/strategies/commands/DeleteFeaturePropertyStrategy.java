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
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureProperty} command.
 */
@Immutable
final class DeleteFeaturePropertyStrategy extends AbstractThingCommandStrategy<DeleteFeatureProperty> {

    /**
     * Constructs a new {@code DeleteFeaturePropertyStrategy} object.
     */
    DeleteFeaturePropertyStrategy() {
        super(DeleteFeatureProperty.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final DeleteFeatureProperty command,
            @Nullable final Metadata metadata) {

        return extractFeature(command, thing)
                .map(feature -> getDeleteFeaturePropertyResult(feature, context, nextRevision, command, thing,
                        metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), command.getFeatureId(),
                                command.getDittoHeaders()), command));
    }

    private Optional<Feature> extractFeature(final DeleteFeatureProperty command, final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getDeleteFeaturePropertyResult(final Feature feature, final Context<ThingId> context,
            final long nextRevision, final DeleteFeatureProperty command, @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        final JsonPointer propertyPointer = command.getPropertyPointer();
        final ThingId thingId = context.getState();
        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return feature.getProperties()
                .flatMap(featureProperties -> featureProperties.getValue(propertyPointer))
                .map(featureProperty -> {
                    final ThingEvent<?> event =
                            FeaturePropertyDeleted.of(thingId, featureId, propertyPointer, nextRevision,
                                    getEventTimestamp(), dittoHeaders, metadata);
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            DeleteFeaturePropertyResponse.of(thingId, featureId, propertyPointer, dittoHeaders), thing);
                    return ResultFactory.<ThingEvent<?>>newMutationResult(command, event, response);
                })
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertyNotFound(thingId, featureId, propertyPointer, dittoHeaders),
                        command));
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteFeatureProperty command,
            @Nullable final Thing previousEntity) {
        return extractFeature(command, previousEntity).flatMap(Feature::getProperties)
                .flatMap(properties -> properties.getValue(command.getPropertyPointer()))
                .flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteFeatureProperty command, @Nullable final Thing newEntity) {
        return Optional.empty();
    }
}
