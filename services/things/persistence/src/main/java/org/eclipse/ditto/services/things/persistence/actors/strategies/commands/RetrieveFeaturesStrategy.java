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
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures} command.
 */
@Immutable
final class RetrieveFeaturesStrategy extends AbstractThingCommandStrategy<RetrieveFeatures> {

    /**
     * Constructs a new {@code RetrieveFeaturesStrategy} object.
     */
    RetrieveFeaturesStrategy() {
        super(RetrieveFeatures.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveFeatures command) {
        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return extractFeatures(thing)
                .map(features -> getFeaturesJson(features, command))
                .map(featuresJson -> RetrieveFeaturesResponse.of(thingId, featuresJson, dittoHeaders))
                .<Result<ThingEvent>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() ->
                        ResultFactory.newErrorResult(ExceptionFactory.featuresNotFound(thingId, dittoHeaders)));
    }

    private Optional<Features> extractFeatures(final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getFeatures();
    }

    private static JsonObject getFeaturesJson(final Features features, final RetrieveFeatures command) {
        return command.getSelectedFields()
                .map(selectedFields -> features.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> features.toJson(command.getImplementedSchemaVersion()));
    }

    @Override
    public Optional<?> previousETagEntity(final RetrieveFeatures command, @Nullable final Thing previousEntity) {
        return nextETagEntity(command, previousEntity);
    }

    @Override
    public Optional<?> nextETagEntity(final RetrieveFeatures command, @Nullable final Thing newEntity) {
        return extractFeatures(newEntity);
    }
}
