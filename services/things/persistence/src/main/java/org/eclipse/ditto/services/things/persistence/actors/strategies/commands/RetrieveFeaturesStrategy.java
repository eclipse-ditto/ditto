/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

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
final class RetrieveFeaturesStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<RetrieveFeatures, Features> {

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

        return extractFeatures(thing)
                .map(features -> getFeaturesJson(features, command))
                .map(featuresJson -> RetrieveFeaturesResponse.of(thingId, featuresJson, dittoHeaders))
                .map(response -> ResultFactory.newQueryResult(command, thing, response, this))
                .orElseGet(() -> ResultFactory.newErrorResult(ExceptionFactory.featuresNotFound(thingId, dittoHeaders)));
    }

    private Optional<Features> extractFeatures(final @Nullable Thing thing) {
        return getThingOrThrow(thing).getFeatures();
    }

    private static JsonObject getFeaturesJson(final Features features, final RetrieveFeatures command) {
        return command.getSelectedFields()
                .map(selectedFields -> features.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> features.toJson(command.getImplementedSchemaVersion()));
    }

    @Override
    public Optional<Features> determineETagEntity(final RetrieveFeatures command, @Nullable final Thing thing) {
        return extractFeatures(thing);
    }
}
