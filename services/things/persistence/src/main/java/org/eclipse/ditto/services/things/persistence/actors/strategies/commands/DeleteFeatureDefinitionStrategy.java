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
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition} command.
 */
@Immutable
final class DeleteFeatureDefinitionStrategy extends
        AbstractThingCommandStrategy<DeleteFeatureDefinition> {

    /**
     * Constructs a new {@code DeleteFeatureDefinitionStrategy} object.
     */
    DeleteFeatureDefinitionStrategy() {
        super(DeleteFeatureDefinition.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final DeleteFeatureDefinition command) {

        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()))
                .map(feature -> getDeleteFeatureDefinitionResult(feature, context, nextRevision, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), command.getFeatureId(),
                                command.getDittoHeaders())));
    }

    private Result<ThingEvent> getDeleteFeatureDefinitionResult(final Feature feature, final Context<ThingId> context,
            final long nextRevision, final DeleteFeatureDefinition command, @Nullable Thing thing) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingId thingId = context.getState();
        final String featureId = feature.getId();

        return feature.getDefinition()
                .map(featureDefinition -> {
                    final ThingEvent event =
                            FeatureDefinitionDeleted.of(thingId, featureId, nextRevision, getEventTimestamp(),
                                    dittoHeaders);
                    final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                            DeleteFeatureDefinitionResponse.of(thingId, featureId, dittoHeaders), thing);
                    return ResultFactory.newMutationResult(command, event, response);
                })
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureDefinitionNotFound(thingId, featureId, dittoHeaders)));
    }

    @Override
    public Optional<?> previousETagEntity(final DeleteFeatureDefinition command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(Thing::getFeatures)
                .flatMap(features -> features.getFeature(command.getFeatureId()))
                .flatMap(Feature::getDefinition);
    }

    @Override
    public Optional<?> nextETagEntity(final DeleteFeatureDefinition command,
            @Nullable final Thing newEntity) {
        return Optional.empty();
    }
}
