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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty} command.
 */
@Immutable
final class RetrieveFeaturePropertyStrategy extends
        AbstractConditionalHeadersCheckingCommandStrategy<RetrieveFeatureProperty, JsonValue> {

    /**
     * Constructs a new {@code RetrieveFeaturePropertyStrategy} object.
     */
    RetrieveFeaturePropertyStrategy() {
        super(RetrieveFeatureProperty.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final RetrieveFeatureProperty command) {
        final String featureId = command.getFeatureId();

        return extractFeature(command, thing)
                .map(feature -> getRetrieveFeaturePropertyResult(feature, context, command, thing))
                .orElseGet(
                        () -> ResultFactory.newErrorResult(ExceptionFactory.featureNotFound(context.getEntityId(),
                                featureId, command.getDittoHeaders())));
    }

    private Optional<Feature> extractFeature(final RetrieveFeatureProperty command, final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent> getRetrieveFeaturePropertyResult(final Feature feature, final Context<ThingId> context,
            final RetrieveFeatureProperty command, @Nullable final Thing thing) {

        return feature.getProperties()
                .map(featureProperties -> getRetrieveFeaturePropertyResult(featureProperties, context, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertiesNotFound(context.getEntityId(), feature.getId(),
                                command.getDittoHeaders())));
    }

    private Result<ThingEvent> getRetrieveFeaturePropertyResult(final JsonObject featureProperties,
            final Context<ThingId> context,
            final RetrieveFeatureProperty command, @Nullable final Thing thing) {

        final String featureId = command.getFeatureId();
        final JsonPointer propertyPointer = command.getPropertyPointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return featureProperties.getValue(propertyPointer)
                .map(featureProperty -> RetrieveFeaturePropertyResponse.of(context.getEntityId(), featureId,
                        propertyPointer, featureProperty, dittoHeaders))
                .<Result<ThingEvent>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertyNotFound(context.getEntityId(), featureId, propertyPointer,
                                dittoHeaders)));
    }

    @Override
    public Optional<JsonValue> determineETagEntity(final RetrieveFeatureProperty command, @Nullable final Thing thing) {
        return extractFeature(command, thing)
                .flatMap(Feature::getProperties)
                .flatMap(featureProperties -> featureProperties.getValue(command.getPropertyPointer()));
    }
}
