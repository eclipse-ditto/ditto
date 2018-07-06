/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties} command.
 */
@NotThreadSafe
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

        final Optional<Features> features = context.getThing().getFeatures();
        final String featureId = command.getFeatureId();
        if (features.isPresent()) {
            final Optional<Feature> feature = features.get().getFeature(featureId);

            if (feature.isPresent()) {
                if (feature.get().getProperties().isPresent()) {
                    final FeaturePropertiesDeleted eventToPersist = FeaturePropertiesDeleted.of(command.getThingId(),
                            featureId, context.getNextRevision(), eventTimestamp(), dittoHeaders);
                    final DeleteFeaturePropertiesResponse response =
                            DeleteFeaturePropertiesResponse.of(context.getThingId(), featureId, dittoHeaders);
                    result = ImmutableResult.of(eventToPersist, response);
                } else {
                    final DittoRuntimeException exception =
                            featurePropertiesNotFound(context.getThingId(), featureId, dittoHeaders);
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
