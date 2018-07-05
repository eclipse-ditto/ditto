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
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;

/**
 * This strategy handles the {@link RetrieveFeatureDefinition} command.
 */
@NotThreadSafe
public final class RetrieveFeatureDefinitionStrategy
        extends AbstractThingCommandStrategy<RetrieveFeatureDefinition> {

    /**
     * Constructs a new {@code RetrieveFeatureDefinitionStrategy} object.
     */
    public RetrieveFeatureDefinitionStrategy() {
        super(RetrieveFeatureDefinition.class);
    }

    @Override
    protected Result doApply(final Context context, final RetrieveFeatureDefinition command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final long nextRevision = context.nextRevision();
        final Optional<Features> optionalFeatures = thing.getFeatures();

        if (optionalFeatures.isPresent()) {
            final Optional<FeatureDefinition> optionalDefinition = optionalFeatures.flatMap(features -> features
                    .getFeature(command.getFeatureId()))
                    .flatMap(Feature::getDefinition);
            if (optionalDefinition.isPresent()) {
                final FeatureDefinition definition = optionalDefinition.get();
                return result(RetrieveFeatureDefinitionResponse.of(thingId, command.getFeatureId(), definition,
                        command.getDittoHeaders()));
            } else {
                return result(featureDefinitionNotFound(thingId, command.getFeatureId(), command.getDittoHeaders()));
            }
        } else {
            return result(featureNotFound(thingId, command.getFeatureId(), command.getDittoHeaders()));
        }
    }

}
