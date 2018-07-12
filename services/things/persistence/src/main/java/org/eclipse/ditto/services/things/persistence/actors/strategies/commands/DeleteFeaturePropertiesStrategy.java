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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Features;
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
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context,
            final DeleteFeatureProperties command) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final CommandStrategy.Result result;

        final Optional<Features> features = context.getThingOrThrow().getFeatures();
        final String featureId = command.getFeatureId();
        if (features.isPresent()) {
            final Optional<Feature> feature = features.get().getFeature(featureId);

            if (feature.isPresent()) {
                if (feature.get().getProperties().isPresent()) {
                    final FeaturePropertiesDeleted eventToPersist = FeaturePropertiesDeleted.of(command.getThingId(),
                            featureId, context.getNextRevision(), getEventTimestamp(), dittoHeaders);
                    final DeleteFeaturePropertiesResponse response =
                            DeleteFeaturePropertiesResponse.of(context.getThingId(), featureId, dittoHeaders);
                    result = ResultFactory.newResult(eventToPersist, response);
                } else {
                    result = ResultFactory.newResult(
                            ExceptionFactory.featurePropertiesNotFound(context.getThingId(), featureId, dittoHeaders));
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
