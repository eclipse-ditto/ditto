/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link RetrieveFeatureDesiredProperties} command.
 */
@Immutable
final class RetrieveFeatureDesiredPropertiesStrategy extends AbstractThingCommandStrategy<RetrieveFeatureDesiredProperties> {

    /**
     * Constructs a new {@code RetrieveFeatureDesiredPropertiesStrategy} object.
     */
    RetrieveFeatureDesiredPropertiesStrategy() {
        super(RetrieveFeatureDesiredProperties.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final RetrieveFeatureDesiredProperties command,
            @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final String featureId = command.getFeatureId();

        return extractFeature(command, thing)
                .map(feature -> getFeatureDesiredProperties(feature, thingId, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(thingId, featureId, command.getDittoHeaders()), command));
    }

    private Optional<Feature> extractFeature(final RetrieveFeatureDesiredProperties command, final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent> getFeatureDesiredProperties(final Feature feature, final ThingId thingId,
            final RetrieveFeatureDesiredProperties command, @Nullable final Thing thing) {

        final String featureId = feature.getId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return feature.getDesiredProperties()
                .map(desiredProperties -> getFeatureDesiredPropertiesJson(desiredProperties, command))
                .map(desiredPropertiesJson -> RetrieveFeatureDesiredPropertiesResponse.of(thingId, featureId,
                        desiredPropertiesJson, dittoHeaders))
                .<Result<ThingEvent>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureDesiredPropertiesNotFound(thingId, featureId, dittoHeaders), command));
    }

    private static JsonObject getFeatureDesiredPropertiesJson(final FeatureProperties desiredProperties,
            final RetrieveFeatureDesiredProperties command) {
        return command.getSelectedFields()
                .map(selectedFields -> desiredProperties.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> desiredProperties.toJson(command.getImplementedSchemaVersion()));
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveFeatureDesiredProperties command,
            @Nullable final Thing previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveFeatureDesiredProperties command, @Nullable final Thing newEntity) {

        return extractFeature(command, newEntity).flatMap(Feature::getDesiredProperties).flatMap(EntityTag::fromEntity);
    }
}
