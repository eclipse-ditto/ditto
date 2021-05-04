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
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperty} command.
 */
@Immutable
final class RetrieveFeaturePropertyStrategy extends AbstractThingCommandStrategy<RetrieveFeatureProperty> {

    /**
     * Constructs a new {@code RetrieveFeaturePropertyStrategy} object.
     */
    RetrieveFeaturePropertyStrategy() {
        super(RetrieveFeatureProperty.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final RetrieveFeatureProperty command,
            @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();

        return extractFeature(command, thing)
                .map(feature -> getRetrieveFeaturePropertyResult(feature, context, command, thing))
                .orElseGet(
                        () -> ResultFactory.newErrorResult(ExceptionFactory.featureNotFound(context.getState(),
                                featureId, command.getDittoHeaders()), command));
    }

    private Optional<Feature> extractFeature(final RetrieveFeatureProperty command, final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getRetrieveFeaturePropertyResult(final Feature feature,
            final Context<ThingId> context,
            final RetrieveFeatureProperty command, @Nullable final Thing thing) {

        return feature.getProperties()
                .map(featureProperties -> getRetrieveFeaturePropertyResult(featureProperties, context, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertiesNotFound(context.getState(), feature.getId(),
                                command.getDittoHeaders()), command));
    }

    private Result<ThingEvent<?>> getRetrieveFeaturePropertyResult(final JsonObject featureProperties,
            final Context<ThingId> context,
            final RetrieveFeatureProperty command, @Nullable final Thing thing) {

        final String featureId = command.getFeatureId();
        final JsonPointer propertyPointer = command.getPropertyPointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return featureProperties.getValue(propertyPointer)
                .map(featureProperty -> RetrieveFeaturePropertyResponse.of(context.getState(), featureId,
                        propertyPointer, featureProperty, dittoHeaders))
                .<Result<ThingEvent<?>>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertyNotFound(context.getState(), featureId, propertyPointer,
                                dittoHeaders), command));
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveFeatureProperty command,
            @Nullable final Thing previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveFeatureProperty command, @Nullable final Thing newEntity) {
        return extractFeature(command, newEntity)
                .flatMap(Feature::getProperties)
                .flatMap(featureProperties -> featureProperties.getValue(command.getPropertyPointer()))
                .flatMap(EntityTag::fromEntity);
    }
}
