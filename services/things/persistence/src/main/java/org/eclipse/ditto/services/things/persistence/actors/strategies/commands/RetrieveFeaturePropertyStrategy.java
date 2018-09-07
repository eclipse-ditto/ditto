/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
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
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;

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
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveFeatureProperty command) {
        final String featureId = command.getFeatureId();

        return extractFeature(command, thing)
                .map(feature -> getRetrieveFeaturePropertyResult(feature, context, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(ExceptionFactory.featureNotFound(context.getThingId(),
                        featureId, command.getDittoHeaders())));
    }

    private Optional<Feature> extractFeature(final RetrieveFeatureProperty command, final @Nullable Thing thing) {
        return getThingOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result getRetrieveFeaturePropertyResult(final Feature feature, final Context context,
            final RetrieveFeatureProperty command, @Nullable final Thing thing) {

        return feature.getProperties()
                .map(featureProperties -> getRetrieveFeaturePropertyResult(featureProperties, context, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertiesNotFound(context.getThingId(), feature.getId(),
                                command.getDittoHeaders())));
    }

    private Result getRetrieveFeaturePropertyResult(final JsonObject featureProperties, final Context context,
            final RetrieveFeatureProperty command, @Nullable final Thing thing) {

        final String featureId = command.getFeatureId();
        final JsonPointer propertyPointer = command.getPropertyPointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return featureProperties.getValue(propertyPointer)
                .map(featureProperty -> RetrieveFeaturePropertyResponse.of(context.getThingId(), featureId,
                        propertyPointer, featureProperty, dittoHeaders))
                .map(response -> ResultFactory.newQueryResult(command, thing, response, this))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertyNotFound(context.getThingId(), featureId, propertyPointer,
                                dittoHeaders)));
    }

    @Override
    public Optional<JsonValue> determineETagEntity(final RetrieveFeatureProperty command, @Nullable final Thing thing) {
        return extractFeature(command, thing)
                .flatMap(Feature::getProperties)
                .flatMap(featureProperties -> featureProperties.getValue(command.getPropertyPointer()));
    }
}
