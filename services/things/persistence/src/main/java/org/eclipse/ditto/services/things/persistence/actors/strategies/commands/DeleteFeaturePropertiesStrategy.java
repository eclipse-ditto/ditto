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

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties} command.
 */
@Immutable
final class DeleteFeaturePropertiesStrategy extends
        AbstractConditionalHeadersCheckingCommandStrategy<DeleteFeatureProperties, FeatureProperties> {

    /**
     * Constructs a new {@code DeleteFeaturePropertiesStrategy} object.
     */
    DeleteFeaturePropertiesStrategy() {
        super(DeleteFeatureProperties.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final DeleteFeatureProperties command) {

        return extractFeature(command, thing)
                .map(feature -> getDeleteFeaturePropertiesResult(feature, context, nextRevision, command))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                                command.getDittoHeaders())));
    }

    private Optional<Feature> extractFeature(final DeleteFeatureProperties command,
            final @Nullable Thing thing) {

        return getThingOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result getDeleteFeaturePropertiesResult(final Feature feature, final Context context,
            final long nextRevision, final DeleteFeatureProperties command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final String thingId = context.getThingId();
        final String featureId = feature.getId();

        return feature.getProperties()
                .map(featureProperties -> ResultFactory.newMutationResult(command,
                        FeaturePropertiesDeleted.of(thingId, featureId, nextRevision, getEventTimestamp(),
                                dittoHeaders), DeleteFeaturePropertiesResponse.of(thingId, featureId, dittoHeaders),
                        this))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featurePropertiesNotFound(thingId, featureId, dittoHeaders)));
    }

    @Override
    public Optional<FeatureProperties> determineETagEntity(final DeleteFeatureProperties command, @Nullable final Thing thing) {
        return extractFeatureProperties(command, thing);
    }

    private Optional<FeatureProperties> extractFeatureProperties(final DeleteFeatureProperties command,
            final @Nullable Thing thing) {

        return extractFeature(command, thing)
                .flatMap(Feature::getProperties);
    }
}
