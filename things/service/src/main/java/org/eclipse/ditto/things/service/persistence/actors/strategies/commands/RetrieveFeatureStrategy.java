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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature} command.
 */
@Immutable
final class RetrieveFeatureStrategy extends AbstractThingCommandStrategy<RetrieveFeature> {

    /**
     * Constructs a new {@code RetrieveFeatureStrategy} object.
     */
    RetrieveFeatureStrategy() {
        super(RetrieveFeature.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final RetrieveFeature command,
            @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();

        return extractFeatures(thing)
                .map(features -> getFeatureResult(features, thingId, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(ExceptionFactory.featureNotFound(thingId,
                        command.getFeatureId(), command.getDittoHeaders()), command));
    }

    private Optional<Features> extractFeatures(final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getFeatures();
    }

    private Result<ThingEvent<?>> getFeatureResult(final Features features, final ThingId thingId,
            final RetrieveFeature command, @Nullable final Thing thing) {

        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return features.getFeature(featureId)
                .map(feature -> getFeatureJson(feature, command))
                .map(featureJson -> RetrieveFeatureResponse.of(thingId, featureId, featureJson, dittoHeaders))
                .<Result<ThingEvent<?>>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(thingId, featureId, dittoHeaders), command));
    }

    private static JsonObject getFeatureJson(final Feature feature, final RetrieveFeature command) {
        return command.getSelectedFields()
                .map(selectedFields -> feature.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> feature.toJson(command.getImplementedSchemaVersion()));
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveFeature command, @Nullable final Thing previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveFeature command, @Nullable final Thing newEntity) {
        return extractFeatures(newEntity)
                .flatMap(features -> features.getFeature(command.getFeatureId()))
                .flatMap(EntityTag::fromEntity);
    }
}
