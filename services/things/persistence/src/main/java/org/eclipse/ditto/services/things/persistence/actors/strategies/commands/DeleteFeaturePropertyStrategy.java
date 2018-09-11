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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty} command.
 */
@Immutable
final class DeleteFeaturePropertyStrategy extends
        AbstractConditionalHeadersCheckingCommandStrategy<DeleteFeatureProperty, JsonValue> {

    /**
     * Constructs a new {@code DeleteFeaturePropertyStrategy} object.
     */
    DeleteFeaturePropertyStrategy() {
        super(DeleteFeatureProperty.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final DeleteFeatureProperty command) {

        return extractFeature(command, thing)
                .map(feature -> getDeleteFeaturePropertyResult(feature, context, nextRevision, command))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                                command.getDittoHeaders())));
    }

    private Optional<Feature> extractFeature(final DeleteFeatureProperty command, final @Nullable Thing thing) {
        return getThingOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Optional<JsonValue> extractFeaturePropertyValue(final DeleteFeatureProperty command,
            final @Nullable Thing thing) {
        return extractFeature(command, thing)
                .flatMap(Feature::getProperties)
                .flatMap(featureProperties -> featureProperties.getValue(command.getPropertyPointer()));
    }

    private Result getDeleteFeaturePropertyResult(final Feature feature, final Context context,
            final long nextRevision, final DeleteFeatureProperty command) {

        final JsonPointer propertyPointer = command.getPropertyPointer();
        final String thingId = context.getThingId();
        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return feature.getProperties()
                .flatMap(featureProperties -> featureProperties.getValue(propertyPointer))
                .map(featureProperty -> ResultFactory.newMutationResult(command,
                        FeaturePropertyDeleted.of(thingId, featureId, propertyPointer, nextRevision,
                                getEventTimestamp(), dittoHeaders),
                        DeleteFeaturePropertyResponse.of(thingId, featureId, propertyPointer, dittoHeaders),
                        this))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertyNotFound(thingId, featureId, propertyPointer, dittoHeaders)));
    }

    @Override
    public Optional<JsonValue> determineETagEntity(final DeleteFeatureProperty command, @Nullable final Thing thing) {
        return extractFeaturePropertyValue(command, thing);
    }
}
