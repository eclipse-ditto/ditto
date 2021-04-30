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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredPropertyResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperty} command.
 */
@Immutable
final class RetrieveFeatureDesiredPropertyStrategy
        extends AbstractThingCommandStrategy<RetrieveFeatureDesiredProperty> {

    /**
     * Constructs a new {@code RetrieveFeatureDesiredPropertyStrategy} object.
     */
    RetrieveFeatureDesiredPropertyStrategy() {
        super(RetrieveFeatureDesiredProperty.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final RetrieveFeatureDesiredProperty command,
            @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();

        return extractFeature(command, thing)
                .map(feature -> getRetrieveFeatureDesiredPropertyResult(feature, context, command, thing))
                .orElseGet(
                        () -> ResultFactory.newErrorResult(ExceptionFactory.featureNotFound(context.getState(),
                                featureId, command.getDittoHeaders()), command));
    }

    private Optional<Feature> extractFeature(final RetrieveFeatureDesiredProperty command,
            final @Nullable Thing thing) {

        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getRetrieveFeatureDesiredPropertyResult(final Feature feature,
            final Context<ThingId> context,
            final RetrieveFeatureDesiredProperty command,
            @Nullable final Thing thing) {

        return feature.getDesiredProperties()
                .map(desiredProperties -> getRetrieveFeatureDesiredPropertyResult(desiredProperties, context, command,
                        thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureDesiredPropertiesNotFound(context.getState(), feature.getId(),
                                command.getDittoHeaders()), command));
    }

    private Result<ThingEvent<?>> getRetrieveFeatureDesiredPropertyResult(final JsonObject featureProperties,
            final Context<ThingId> context,
            final RetrieveFeatureDesiredProperty command,
            @Nullable final Thing thing) {

        final String featureId = command.getFeatureId();
        final JsonPointer propertyPointer = command.getDesiredPropertyPointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return featureProperties.getValue(propertyPointer)
                .map(featureDesiredProperty -> RetrieveFeatureDesiredPropertyResponse.of(context.getState(), featureId,
                        propertyPointer, featureDesiredProperty, dittoHeaders))
                .<Result<ThingEvent<?>>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureDesiredPropertyNotFound(context.getState(), featureId, propertyPointer,
                                dittoHeaders), command));
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveFeatureDesiredProperty command,
            @Nullable final Thing previousEntity) {

        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveFeatureDesiredProperty command,
            @Nullable final Thing newEntity) {

        return extractFeature(command, newEntity)
                .flatMap(Feature::getDesiredProperties)
                .flatMap(featureProperties -> featureProperties.getValue(command.getDesiredPropertyPointer()))
                .flatMap(EntityTag::fromEntity);
    }
}
