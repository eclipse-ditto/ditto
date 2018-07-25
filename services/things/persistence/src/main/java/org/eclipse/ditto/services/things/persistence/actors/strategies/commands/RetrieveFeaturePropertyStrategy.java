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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty} command.
 */
@Immutable
final class RetrieveFeaturePropertyStrategy extends AbstractCommandStrategy<RetrieveFeatureProperty> {

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

        return getThingOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .map(feature -> getRetrieveFeaturePropertyResult(feature, context, command))
                .orElseGet(() -> ResultFactory.newResult(ExceptionFactory.featureNotFound(context.getThingId(),
                        featureId, command.getDittoHeaders())));
    }

    private static Result getRetrieveFeaturePropertyResult(final Feature feature, final Context context,
            final RetrieveFeatureProperty command) {

        return feature.getProperties()
                .map(featureProperties -> getRetrieveFeaturePropertyResult(featureProperties, context, command))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.featurePropertiesNotFound(context.getThingId(), feature.getId(),
                                command.getDittoHeaders())));
    }

    private static Result getRetrieveFeaturePropertyResult(final JsonObject featureProperties, final Context context,
            final RetrieveFeatureProperty command) {

        final String featureId = command.getFeatureId();
        final JsonPointer propertyPointer = command.getPropertyPointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return featureProperties.getValue(propertyPointer)
                .map(featureProperty -> RetrieveFeaturePropertyResponse.of(context.getThingId(), featureId,
                        propertyPointer, featureProperty, dittoHeaders))
                .map(ResultFactory::newResult)
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.featurePropertyNotFound(context.getThingId(), featureId, propertyPointer,
                                dittoHeaders)));
    }

}
