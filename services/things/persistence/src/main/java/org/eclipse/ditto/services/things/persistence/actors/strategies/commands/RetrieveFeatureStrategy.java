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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.id.ThingId;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeature} command.
 */
@Immutable
final class RetrieveFeatureStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<RetrieveFeature, Feature> {

    /**
     * Constructs a new {@code RetrieveFeatureStrategy} object.
     */
    RetrieveFeatureStrategy() {
        super(RetrieveFeature.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveFeature command) {
        final ThingId thingId = context.getThingEntityId();

        return extractFeatures(thing)
                .map(features -> getFeatureResult(features, thingId, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(ExceptionFactory.featureNotFound(thingId,
                        command.getFeatureId(), command.getDittoHeaders())));
    }

    private Optional<Features> extractFeatures(final @Nullable Thing thing) {
        return getThingOrThrow(thing).getFeatures();
    }

    private Result getFeatureResult(final Features features, final ThingId thingId,
            final RetrieveFeature command, @Nullable final Thing thing) {

        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return features.getFeature(featureId)
                .map(feature -> getFeatureJson(feature, command))
                .map(featureJson -> RetrieveFeatureResponse.of(thingId, featureId, featureJson, dittoHeaders))
                .map(response -> ResultFactory.newQueryResult(command, thing, response, this))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(thingId, featureId, dittoHeaders)));
    }

    private static JsonObject getFeatureJson(final Feature feature, final RetrieveFeature command) {
        return command.getSelectedFields()
                .map(selectedFields -> feature.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> feature.toJson(command.getImplementedSchemaVersion()));
    }

    @Override
    public Optional<Feature> determineETagEntity(final RetrieveFeature command, @Nullable final Thing thing) {
        return extractFeatures(thing)
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }
}
