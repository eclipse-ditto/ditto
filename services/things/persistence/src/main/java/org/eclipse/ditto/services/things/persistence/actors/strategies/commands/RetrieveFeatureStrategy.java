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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeature} command.
 */
@Immutable
final class RetrieveFeatureStrategy extends AbstractCommandStrategy<RetrieveFeature> {

    /**
     * Constructs a new {@code RetrieveFeatureStrategy} object.
     */
    RetrieveFeatureStrategy() {
        super(RetrieveFeature.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveFeature command) {
        final String thingId = context.getThingId();

        return getThingOrThrow(thing).getFeatures()
                .map(features -> getFeatureResult(features, thingId, command))
                .orElseGet(() -> ResultFactory.newResult(ExceptionFactory.featureNotFound(thingId,
                        command.getFeatureId(), command.getDittoHeaders())));
    }

    private static Result getFeatureResult(final Features features, final String thingId,
            final RetrieveFeature command) {

        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return features.getFeature(featureId)
                .map(feature -> getFeatureJson(feature, command))
                .map(featureJson -> RetrieveFeatureResponse.of(thingId, featureId, featureJson, dittoHeaders))
                .map(ResultFactory::newResult)
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.featureNotFound(thingId, featureId, dittoHeaders)));
    }

    private static JsonObject getFeatureJson(final Feature feature, final RetrieveFeature command) {
        return command.getSelectedFields()
                .map(selectedFields -> feature.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> feature.toJson(command.getImplementedSchemaVersion()));
    }

}
