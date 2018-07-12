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
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context,
            final DeleteFeatureDefinition command) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final CommandStrategy.Result result;

        final Thing thing = context.getThingOrThrow();
        final String featureId = command.getFeatureId();

        final Optional<Features> features = thing.getFeatures();
        if (features.isPresent()) {
            final Optional<Feature> feature = features.get().getFeature(featureId);

            if (feature.isPresent()) {
                if (feature.get().getDefinition().isPresent()) {
                    final FeatureDefinitionDeleted eventToPersist = FeatureDefinitionDeleted.of(command.getThingId(),
                            featureId, context.getNextRevision(), getEventTimestamp(), dittoHeaders);
                    final DeleteFeatureDefinitionResponse response = DeleteFeatureDefinitionResponse.of(context
                            .getThingId(), featureId, dittoHeaders);

                    result = ResultFactory.newResult(eventToPersist, response);
                } else {
                    result = ResultFactory.newResult(
                            ExceptionFactory.featureDefinitionNotFound(context.getThingId(), featureId,
                                    dittoHeaders));
                }
            } else {
                result = ResultFactory.newResult(
                        ExceptionFactory.featureNotFound(context.getThingId(), featureId, dittoHeaders));
            }
        } else {
            result = ResultFactory.newResult(ExceptionFactory.featureNotFound(context.getThingId(),
                    featureId, dittoHeaders));
        }

        return result;
    }

}
