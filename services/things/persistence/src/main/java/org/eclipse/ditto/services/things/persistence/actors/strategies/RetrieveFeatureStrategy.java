/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeature} command.
 */
@NotThreadSafe
final class RetrieveFeatureStrategy extends AbstractCommandStrategy<RetrieveFeature> {

    /**
     * Constructs a new {@code RetrieveFeatureStrategy} object.
     */
    RetrieveFeatureStrategy() {
        super(RetrieveFeature.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final RetrieveFeature command) {
        final CommandStrategy.Result result;

        final Optional<Feature> feature =
                context.getThing().getFeatures().flatMap(fs -> fs.getFeature(command.getFeatureId()));
        if (feature.isPresent()) {
            final Feature f = feature.get();
            final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
            final JsonObject featureJson = selectedFields
                    .map(sf -> f.toJson(command.getImplementedSchemaVersion(), sf))
                    .orElseGet(() -> f.toJson(command.getImplementedSchemaVersion()));
            final RetrieveFeatureResponse response =
                    RetrieveFeatureResponse.of(context.getThingId(), command.getFeatureId(), featureJson,
                            command.getDittoHeaders());
            result = ImmutableResult.of(response);
        } else {
            final DittoRuntimeException exception =
                    featureNotFound(context.getThingId(), command.getFeatureId(), command.getDittoHeaders());
            result = ImmutableResult.of(exception);
        }

        return result;
    }

}
