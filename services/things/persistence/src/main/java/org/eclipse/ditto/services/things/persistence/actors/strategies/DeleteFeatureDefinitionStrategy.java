/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition} command.
 */
@NotThreadSafe
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

        final Optional<Features> features = context.getThing().getFeatures();
        if (features.isPresent()) {
            final Optional<Feature> feature = features.get().getFeature(command.getFeatureId());

            if (feature.isPresent()) {
                if (feature.get().getDefinition().isPresent()) {
                    final FeatureDefinitionDeleted eventToPersist = FeatureDefinitionDeleted.of(command.getThingId(),
                            command.getFeatureId(), context.getNextRevision(), eventTimestamp(), dittoHeaders);
                    final DeleteFeatureDefinitionResponse response = DeleteFeatureDefinitionResponse.of(context
                            .getThingId(), command.getFeatureId(), dittoHeaders);
                    result = ImmutableResult.of(eventToPersist, response);
                } else {
                    final DittoRuntimeException exception =
                            featureDefinitionNotFound(context.getThingId(), command.getFeatureId(), dittoHeaders);
                    result = ImmutableResult.of(exception);
                }
            } else {
                final DittoRuntimeException exception =
                        featureNotFound(context.getThingId(), command.getFeatureId(), dittoHeaders);
                result = ImmutableResult.of(exception);
            }
        } else {
            final DittoRuntimeException exception =
                    featureNotFound(context.getThingId(), command.getFeatureId(), dittoHeaders);
            result = ImmutableResult.of(exception);
        }

        return result;
    }

}
