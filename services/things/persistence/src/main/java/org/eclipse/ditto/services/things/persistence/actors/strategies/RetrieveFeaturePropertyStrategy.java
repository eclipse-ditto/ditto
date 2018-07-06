/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty} command.
 */
@NotThreadSafe
final class RetrieveFeaturePropertyStrategy extends AbstractCommandStrategy<RetrieveFeatureProperty> {

    /**
     * Constructs a new {@code RetrieveFeaturePropertyStrategy} object.
     */
    RetrieveFeaturePropertyStrategy() {
        super(RetrieveFeatureProperty.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context,
            final RetrieveFeatureProperty command) {
        final CommandStrategy.Result result;

        final Optional<Feature> featureOptional = context.getThing().getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
        if (featureOptional.isPresent()) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<FeatureProperties> optionalProperties = featureOptional.flatMap(Feature::getProperties);
            if (optionalProperties.isPresent()) {
                final FeatureProperties properties = optionalProperties.get();
                final JsonPointer jsonPointer = command.getPropertyPointer();
                final Optional<JsonValue> propertyJson = properties.getValue(jsonPointer);
                if (propertyJson.isPresent()) {
                    final RetrieveFeaturePropertyResponse response =
                            RetrieveFeaturePropertyResponse.of(context.getThingId(), command.getFeatureId(),
                                    jsonPointer,
                                    propertyJson.get(), dittoHeaders);
                    result = ImmutableResult.of(response);
                } else {
                    final DittoRuntimeException exception = featurePropertyNotFound(context.getThingId(),
                            command.getFeatureId(), jsonPointer, dittoHeaders);
                    result = ImmutableResult.of(exception);
                }
            } else {
                final DittoRuntimeException exception = featurePropertiesNotFound(context.getThingId(),
                        command.getFeatureId(), command.getDittoHeaders());
                result = ImmutableResult.of(exception);
            }
        } else {
            final DittoRuntimeException exception = featureNotFound(context.getThingId(), command.getFeatureId(),
                    command.getDittoHeaders());
            result = ImmutableResult.of(exception);
        }

        return result;
    }

}
