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
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link RetrieveFeatureProperties} command.
 */
@Immutable
final class RetrieveFeaturePropertiesStrategy extends AbstractThingCommandStrategy<RetrieveFeatureProperties> {

    /**
     * Constructs a new {@code RetrieveFeaturePropertiesStrategy} object.
     */
    RetrieveFeaturePropertiesStrategy() {
        super(RetrieveFeatureProperties.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final RetrieveFeatureProperties command,
            @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final String featureId = command.getFeatureId();

        return extractFeature(command, thing)
                .map(feature -> getFeatureProperties(feature, thingId, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(thingId, featureId, command.getDittoHeaders()), command));
    }

    private Optional<Feature> extractFeature(final RetrieveFeatureProperties command, final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getFeatureProperties(final Feature feature, final ThingId thingId,
            final RetrieveFeatureProperties command, @Nullable final Thing thing) {

        final String featureId = feature.getId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return feature.getProperties()
                .map(featureProperties -> getFeaturePropertiesJson(featureProperties, command))
                .map(featurePropertiesJson -> RetrieveFeaturePropertiesResponse.of(thingId, featureId,
                        featurePropertiesJson, dittoHeaders))
                .<Result<ThingEvent<?>>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertiesNotFound(thingId, featureId, dittoHeaders), command));
    }

    private static JsonObject getFeaturePropertiesJson(final FeatureProperties featureProperties,
            final RetrieveFeatureProperties command) {
        return command.getSelectedFields()
                .map(selectedFields -> featureProperties.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> featureProperties.toJson(command.getImplementedSchemaVersion()));
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveFeatureProperties command,
            @Nullable final Thing previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveFeatureProperties command, @Nullable final Thing newEntity) {

        return extractFeature(command, newEntity).flatMap(Feature::getProperties).flatMap(EntityTag::fromEntity);
    }
}
