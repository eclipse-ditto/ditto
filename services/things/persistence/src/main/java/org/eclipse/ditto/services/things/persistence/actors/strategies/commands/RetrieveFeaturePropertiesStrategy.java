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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;

/**
 * This strategy handles the {@link RetrieveFeatureProperties} command.
 */
@Immutable
final class RetrieveFeaturePropertiesStrategy extends AbstractCommandStrategy<RetrieveFeatureProperties> {

    /**
     * Constructs a new {@code RetrieveFeaturePropertiesStrategy} object.
     */
    RetrieveFeaturePropertiesStrategy() {
        super(RetrieveFeatureProperties.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveFeatureProperties command) {
        final String thingId = context.getThingId();
        final String featureId = command.getFeatureId();

        return getThingOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .map(feature -> getFeatureProperties(feature, thingId, command))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.featureNotFound(thingId, featureId, command.getDittoHeaders())));
    }

    private static Result getFeatureProperties(final Feature feature, final String thingId,
            final RetrieveFeatureProperties command) {

        final String featureId = feature.getId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return feature.getProperties()
                .map(featureProperties -> RetrieveFeaturePropertiesResponse.of(thingId, featureId,
                        featureProperties, dittoHeaders))
                .map(ResultFactory::newResult)
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.featurePropertiesNotFound(thingId, featureId, dittoHeaders)));
    }

}
