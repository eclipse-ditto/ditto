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
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures} command.
 */
@Immutable
final class RetrieveFeaturesStrategy extends AbstractCommandStrategy<RetrieveFeatures> {

    /**
     * Constructs a new {@code RetrieveFeaturesStrategy} object.
     */
    RetrieveFeaturesStrategy() {
        super(RetrieveFeatures.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveFeatures command) {
        final String thingId = context.getThingId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return getThingOrThrow(thing).getFeatures()
                .map(features -> getFeaturesJson(features, command))
                .map(featuresJson -> RetrieveFeaturesResponse.of(thingId, featuresJson, dittoHeaders))
                .map(ResultFactory::newResult)
                .orElseGet(() -> ResultFactory.newResult(ExceptionFactory.featuresNotFound(thingId, dittoHeaders)));
    }

    private static JsonObject getFeaturesJson(final Features features, final RetrieveFeatures command) {
        return command.getSelectedFields()
                .map(selectedFields -> features.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> features.toJson(command.getImplementedSchemaVersion()));
    }

}
