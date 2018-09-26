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
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition} command.
 */
@Immutable
final class ModifyFeatureDefinitionStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<ModifyFeatureDefinition, FeatureDefinition> {

    /**
     * Constructs a new {@code ModifyFeatureDefinitionStrategy} object.
     */
    ModifyFeatureDefinitionStrategy() {
        super(ModifyFeatureDefinition.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyFeatureDefinition command) {
        final String featureId = command.getFeatureId();

        return extractFeature(command, thing)
                .map(feature -> getModifyOrCreateResult(feature, context, nextRevision, command))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getThingId(), featureId, command.getDittoHeaders())));
    }

    private Optional<Feature> extractFeature(final ModifyFeatureDefinition command, final @Nullable Thing thing) {
        return getThingOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result getModifyOrCreateResult(final Feature feature, final Context context,
            final long nextRevision, final ModifyFeatureDefinition command) {

        return feature.getDefinition()
                .map(definition -> getModifyResult(context, nextRevision, command))
                .orElseGet(() -> getCreateResult(context, nextRevision, command));
    }

    private Result getModifyResult(final Context context, final long nextRevision,
            final ModifyFeatureDefinition command) {
        final String thingId = context.getThingId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final String featureId = command.getFeatureId();

        return ResultFactory.newMutationResult(command,
                FeatureDefinitionModified.of(thingId, featureId, command.getDefinition(),
                        nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyFeatureDefinitionResponse.modified(thingId, featureId, dittoHeaders), this);
    }

    private Result getCreateResult(final Context context, final long nextRevision,
            final ModifyFeatureDefinition command) {
        final String thingId = context.getThingId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final String featureId = command.getFeatureId();

        return ResultFactory.newMutationResult(command, FeatureDefinitionCreated.of(thingId, featureId, command.getDefinition(),
                nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyFeatureDefinitionResponse.created(thingId, featureId, command.getDefinition(), dittoHeaders),
                this);
    }

    @Override
    public Optional<FeatureDefinition> determineETagEntity(final ModifyFeatureDefinition command,
            @Nullable final Thing thing) {
        return extractFeature(command, thing).flatMap(Feature::getDefinition);
    }
}
