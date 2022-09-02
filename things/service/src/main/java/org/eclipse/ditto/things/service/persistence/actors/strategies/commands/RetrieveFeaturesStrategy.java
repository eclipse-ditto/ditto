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

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures} command.
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
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final RetrieveFeatures command,
            @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return extractFeatures(thing)
                .map(features -> getFeaturesJson(features, command))
                .map(featuresJson -> RetrieveFeaturesResponse.of(thingId, featuresJson, dittoHeaders))
                .<Result<ThingEvent<?>>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() -> ResultFactory
                        .newErrorResult(ExceptionFactory.featuresNotFound(thingId, dittoHeaders), command));
    }

    private Optional<Features> extractFeatures(@Nullable final Thing thing) {
        return getEntityOrThrow(thing).getFeatures();
    }

    private static JsonObject getFeaturesJson(final Features features, final RetrieveFeatures command) {
        return command.getSelectedFields()
                .map(selectedFields -> {
                    final JsonFieldSelector expandedFieldSelector = expandFeatureIdWildcard(selectedFields, features);
                    return features.toJson(command.getImplementedSchemaVersion(), expandedFieldSelector);
                })
                .orElseGet(() -> features.toJson(command.getImplementedSchemaVersion()));
    }

    private static JsonFieldSelector expandFeatureIdWildcard(final JsonFieldSelector selectedFields,
            final Features features) {
        // add /features level for expansion
        final List<JsonPointer> normalized = selectedFields.getPointers()
                .stream()
                .map(selector -> Thing.JsonFields.FEATURES.getPointer().append(selector))
                .toList();
        final JsonFieldSelector expandedPointers =
                ThingsModelFactory.expandFeatureIdWildcards(features, JsonFactory.newFieldSelector(normalized));
        // and remove it again because field selectors are relative to /features
        final List<JsonPointer> denormalized =
                expandedPointers.getPointers()
                        .stream()
                        .map(p -> p.getSubPointer(1).orElse(JsonPointer.empty()))
                        .toList();
        return JsonFactory.newFieldSelector(denormalized);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveFeatures command, @Nullable final Thing previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveFeatures command, @Nullable final Thing newEntity) {
        return extractFeatures(newEntity).flatMap(EntityTag::fromEntity);
    }
}
