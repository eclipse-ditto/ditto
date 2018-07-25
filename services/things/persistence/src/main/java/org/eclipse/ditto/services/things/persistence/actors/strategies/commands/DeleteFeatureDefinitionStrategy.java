/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition} command.
 */
@Immutable
final class DeleteFeatureDefinitionStrategy extends AbstractCommandStrategy<DeleteFeatureDefinition> {

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
                        command.getDittoHeaders()))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                                command.getDittoHeaders())));
    }

    private static Result getDeleteFeatureDefinitionResult(final Feature feature, final Context context,
            final long nextRevision, final DittoHeaders dittoHeaders) {

        final String thingId = context.getThingId();
        final String featureId = feature.getId();

        return feature.getDefinition()
                .map(featureDefinition -> ResultFactory.newResult(
                        FeatureDefinitionDeleted.of(thingId, featureId, nextRevision, getEventTimestamp(),
                                dittoHeaders), DeleteFeatureDefinitionResponse.of(thingId, featureId, dittoHeaders)))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.featureDefinitionNotFound(thingId, featureId, dittoHeaders)));
    }

}
