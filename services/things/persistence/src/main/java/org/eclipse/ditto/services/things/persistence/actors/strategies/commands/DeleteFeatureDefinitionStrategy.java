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
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition} command.
 */
@Immutable
final class DeleteFeatureDefinitionStrategy extends
        AbstractConditionalHeadersCheckingCommandStrategy<DeleteFeatureDefinition, FeatureDefinition> {

    /**
     * Constructs a new {@code DeleteFeatureDefinitionStrategy} object.
     */
    DeleteFeatureDefinitionStrategy() {
        super(DeleteFeatureDefinition.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final DeleteFeatureDefinition command) {

        return getThingOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()))
                .map(feature -> getDeleteFeatureDefinitionResult(feature, context, nextRevision,
                        command))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getThingEntityId(), command.getFeatureId(),
                                command.getDittoHeaders())));
    }

    private Result getDeleteFeatureDefinitionResult(final Feature feature, final Context context,
            final long nextRevision, final DeleteFeatureDefinition command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingId thingId = context.getThingEntityId();
        final String featureId = feature.getId();

        return feature.getDefinition()
                .map(featureDefinition -> ResultFactory.newMutationResult(command,
                        FeatureDefinitionDeleted.of(thingId, featureId, nextRevision, getEventTimestamp(),
                                dittoHeaders), DeleteFeatureDefinitionResponse.of(thingId, featureId, dittoHeaders), this))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureDefinitionNotFound(thingId, featureId, dittoHeaders)));
    }

    @Override
    public Optional<FeatureDefinition> determineETagEntity(final DeleteFeatureDefinition command, @Nullable final Thing thing) {
        return getThingOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()))
                .flatMap(Feature::getDefinition);
    }
}
