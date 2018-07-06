/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty} command.
 */
@NotThreadSafe
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

        final Optional<Features> featuresOptional = context.getThing().getFeatures();
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
                            featureId, propertyJsonPointer, context.getNextRevision(), eventTimestamp(), dittoHeaders);
                    final DeleteFeaturePropertyResponse response =
                            DeleteFeaturePropertyResponse.of(context.getThingId(), featureId, propertyJsonPointer,
                                    dittoHeaders);
                    result = ImmutableResult.of(eventToPersist, response);
                } else {
                    final DittoRuntimeException exception =
                            featurePropertyNotFound(context.getThingId(), featureId, propertyJsonPointer, dittoHeaders);
                    result = ImmutableResult.of(exception);
                }
            } else {
                final DittoRuntimeException exception =
                        featureNotFound(context.getThingId(), featureId, dittoHeaders);
                result = ImmutableResult.of(exception);
            }
        } else {
            final DittoRuntimeException exception =
                    featureNotFound(context.getThingId(), featureId, dittoHeaders);
            result = ImmutableResult.of(exception);
        }

        return result;
    }

}
