/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures} command.
 */
@NotThreadSafe
final class RetrieveFeaturesStrategy extends AbstractThingCommandStrategy<RetrieveFeatures> {

    /**
     * Constructs a new {@code RetrieveFeaturesStrategy} object.
     */
    public RetrieveFeaturesStrategy() {
        super(RetrieveFeatures.class);
    }

    @Override
    protected Result doApply(final Context context, final RetrieveFeatures command) {
        final Result result;

        final Optional<Features> optionalFeatures = context.getThing().getFeatures();
        if (optionalFeatures.isPresent()) {
            final Features features = optionalFeatures.get();
            final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
            final JsonObject featuresJson = selectedFields
                    .map(sf -> features.toJson(command.getImplementedSchemaVersion(), sf))
                    .orElseGet(() -> features.toJson(command.getImplementedSchemaVersion()));
            final RetrieveFeaturesResponse response =
                    RetrieveFeaturesResponse.of(context.getThingId(), featuresJson, command.getDittoHeaders());
            result = ImmutableResult.of(response);
        } else {
            final DittoRuntimeException exception =
                    featuresNotFound(context.getThingId(), command.getDittoHeaders());
            result = ImmutableResult.of(exception);
        }

        return result;
    }

}
