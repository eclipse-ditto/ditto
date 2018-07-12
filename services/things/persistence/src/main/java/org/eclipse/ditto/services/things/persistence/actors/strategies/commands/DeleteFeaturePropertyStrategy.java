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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty} command.
 */
@Immutable
final class DeleteFeaturePropertyStrategy extends AbstractCommandStrategy<DeleteFeatureProperty> {

    /**
     * Constructs a new {@code DeleteFeaturePropertyStrategy} object.
     */
    DeleteFeaturePropertyStrategy() {
        super(DeleteFeatureProperty.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context,
            final DeleteFeatureProperty command) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final CommandStrategy.Result result;

        final Optional<Features> featuresOptional = context.getThingOrThrow().getFeatures();
        final String featureId = command.getFeatureId();
        if (featuresOptional.isPresent()) {
            final Optional<Feature> featureOptional =
                    featuresOptional.flatMap(features -> features.getFeature(featureId));
            if (featureOptional.isPresent()) {
                final JsonPointer propertyJsonPointer = command.getPropertyPointer();
                final Feature feature = featureOptional.get();
                final boolean containsProperty = feature.getProperties()
                        .filter(featureProperties -> featureProperties.contains(propertyJsonPointer))
                        .isPresent();
                if (containsProperty) {
                    final FeaturePropertyDeleted eventToPersist = FeaturePropertyDeleted.of(command.getThingId(),
                            featureId, propertyJsonPointer, context.getNextRevision(), getEventTimestamp(),
                            dittoHeaders);
                    final DeleteFeaturePropertyResponse response =
                            DeleteFeaturePropertyResponse.of(context.getThingId(), featureId, propertyJsonPointer,
                                    dittoHeaders);
                    result = ResultFactory.newResult(eventToPersist, response);
                } else {
                    result = ResultFactory.newResult(
                            ExceptionFactory.featurePropertyNotFound(context.getThingId(), featureId,
                                    propertyJsonPointer, dittoHeaders));
                }
            } else {
                result = ResultFactory.newResult(
                        ExceptionFactory.featureNotFound(context.getThingId(), featureId, dittoHeaders));
            }
        } else {
            result = ResultFactory.newResult(
                    ExceptionFactory.featureNotFound(context.getThingId(), featureId, dittoHeaders));
        }

        return result;
    }

}
