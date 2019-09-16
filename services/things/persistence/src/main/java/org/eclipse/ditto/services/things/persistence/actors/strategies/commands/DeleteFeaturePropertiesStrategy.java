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
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties} command.
 */
@Immutable
final class DeleteFeaturePropertiesStrategy extends
        AbstractThingCommandStrategy<DeleteFeatureProperties> {

    /**
     * Constructs a new {@code DeleteFeaturePropertiesStrategy} object.
     */
    DeleteFeaturePropertiesStrategy() {
        super(DeleteFeatureProperties.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final DeleteFeatureProperties command) {

        return extractFeature(command, thing)
                .map(feature -> getDeleteFeaturePropertiesResult(feature, context, nextRevision, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), command.getFeatureId(),
                                command.getDittoHeaders())));
    }

    private Optional<Feature> extractFeature(final DeleteFeatureProperties command,
            final @Nullable Thing thing) {

        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent> getDeleteFeaturePropertiesResult(final Feature feature, final Context<ThingId> context,
            final long nextRevision, final DeleteFeatureProperties command, @Nullable final Thing thing) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingId thingId = context.getState();
        final String featureId = feature.getId();

        return feature.getProperties()
                .map(featureProperties -> {
                    final ThingEvent event =
                            FeaturePropertiesDeleted.of(thingId, featureId, nextRevision, getEventTimestamp(),
                                    dittoHeaders);
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            DeleteFeaturePropertiesResponse.of(thingId, featureId, dittoHeaders), thing);
                    return ResultFactory.newMutationResult(command, event, response);
                })
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertiesNotFound(thingId, featureId, dittoHeaders)));
    }

    @Override
    public Optional<?> previousETagEntity(final DeleteFeatureProperties command, @Nullable final Thing previousEntity) {
        return extractFeature(command, previousEntity).flatMap(Feature::getProperties);
    }

    @Override
    public Optional<?> nextETagEntity(final DeleteFeatureProperties command,
            @Nullable final Thing newEntity) {
        return Optional.empty();
    }

}
