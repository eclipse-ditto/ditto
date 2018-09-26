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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.Thing;
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
                        ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                                command.getDittoHeaders())));
    }

    private Result getDeleteFeatureDefinitionResult(final Feature feature, final Context context,
            final long nextRevision, final DeleteFeatureDefinition command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final String thingId = context.getThingId();
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
