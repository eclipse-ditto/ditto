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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;

/**
 * This strategy handles the {@link RetrieveFeatureProperties} command.
 */
@Immutable
final class RetrieveFeaturePropertiesStrategy extends
        AbstractConditionalHeadersCheckingCommandStrategy<RetrieveFeatureProperties, FeatureProperties> {

    /**
     * Constructs a new {@code RetrieveFeaturePropertiesStrategy} object.
     */
    RetrieveFeaturePropertiesStrategy() {
        super(RetrieveFeatureProperties.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveFeatureProperties command) {
        final ThingId thingId = context.getThingEntityId();
        final String featureId = command.getFeatureId();

        return extractFeature(command, thing)
                .map(feature -> getFeatureProperties(feature, thingId, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(thingId, featureId, command.getDittoHeaders())));
    }

    private Optional<Feature> extractFeature(final RetrieveFeatureProperties command, final @Nullable Thing thing) {
        return getThingOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result getFeatureProperties(final Feature feature, final ThingId thingId,
            final RetrieveFeatureProperties command, @Nullable final Thing thing) {

        final String featureId = feature.getId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return feature.getProperties()
                .map(featureProperties -> RetrieveFeaturePropertiesResponse.of(thingId, featureId,
                        featureProperties, dittoHeaders))
                .map(response -> ResultFactory.newQueryResult(command, thing, response, this))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertiesNotFound(thingId, featureId, dittoHeaders)));
    }

    @Override
    public Optional<FeatureProperties> determineETagEntity(final RetrieveFeatureProperties command,
            @Nullable final Thing thing) {

        return extractFeature(command, thing)
                .flatMap(Feature::getProperties);
    }
}
