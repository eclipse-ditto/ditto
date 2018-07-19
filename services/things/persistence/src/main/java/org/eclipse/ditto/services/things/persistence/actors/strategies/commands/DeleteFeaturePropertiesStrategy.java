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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties} command.
 */
@Immutable
final class DeleteFeaturePropertiesStrategy extends AbstractCommandStrategy<DeleteFeatureProperties> {

    /**
     * Constructs a new {@code DeleteFeaturePropertiesStrategy} object.
     */
    DeleteFeaturePropertiesStrategy() {
        super(DeleteFeatureProperties.class);
    }

    @Override
    protected Result doApply(final Context context, final DeleteFeatureProperties command) {
        final Thing thing = context.getThingOrThrow();

        return thing.getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()))
                .map(feature -> getDeleteFeaturePropertiesResult(feature, context, command.getDittoHeaders()))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                                command.getDittoHeaders())));
    }

    private static Result getDeleteFeaturePropertiesResult(final Feature feature, final Context context,
            final DittoHeaders dittoHeaders) {

        final String thingId = context.getThingId();
        final String featureId = feature.getId();

        return feature.getProperties()
                .map(featureProperties -> ResultFactory.newResult(
                        FeaturePropertiesDeleted.of(thingId, featureId, context.getNextRevision(), getEventTimestamp(),
                                dittoHeaders), DeleteFeaturePropertiesResponse.of(thingId, featureId, dittoHeaders)))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.featurePropertiesNotFound(thingId, featureId, dittoHeaders)));
    }

}
