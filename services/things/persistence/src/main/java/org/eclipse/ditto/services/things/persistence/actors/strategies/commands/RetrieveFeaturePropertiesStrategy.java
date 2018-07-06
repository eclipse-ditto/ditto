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

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;

/**
 * This strategy handles the {@link RetrieveFeatureProperties} command.
 */
@NotThreadSafe
final class RetrieveFeaturePropertiesStrategy extends AbstractCommandStrategy<RetrieveFeatureProperties> {

    /**
     * Constructs a new {@code RetrieveFeaturePropertiesStrategy} object.
     */
    RetrieveFeaturePropertiesStrategy() {
        super(RetrieveFeatureProperties.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context,
            final RetrieveFeatureProperties command) {

        final String thingId = context.getThingId();
        final Thing thing = context.getThing();

        final Optional<Features> optionalFeatures = thing.getFeatures();

        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (optionalFeatures.isPresent()) {
            final Optional<FeatureProperties> optionalProperties = optionalFeatures.flatMap(features -> features
                    .getFeature(featureId))
                    .flatMap(Feature::getProperties);
            if (optionalProperties.isPresent()) {
                final FeatureProperties properties = optionalProperties.get();
                final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
                final JsonObject propertiesJson = selectedFields
                        .map(sf -> properties.toJson(command.getImplementedSchemaVersion(), sf))
                        .orElseGet(() -> properties.toJson(command.getImplementedSchemaVersion()));
                return ImmutableResult.of(
                        RetrieveFeaturePropertiesResponse.of(thingId, featureId, propertiesJson, dittoHeaders));
            } else {
                return ImmutableResult.of(featurePropertiesNotFound(thingId, featureId, dittoHeaders));
            }
        } else {
            return ImmutableResult.of(featureNotFound(thingId, featureId, dittoHeaders));
        }
    }
}
