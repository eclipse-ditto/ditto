/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition} command.
 */
@NotThreadSafe
final class ModifyFeatureDefinitionStrategy extends AbstractThingCommandStrategy<ModifyFeatureDefinition> {

    /**
     * Constructs a new {@code ModifyFeatureDefinitionStrategy} object.
     */
    public ModifyFeatureDefinitionStrategy() {
        super(ModifyFeatureDefinition.class);
    }

    @Override
    protected Result doApply(final Context context, final ModifyFeatureDefinition command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Result result;

        final Optional<Features> features = context.getThing().getFeatures();
        if (features.isPresent()) {
            final Optional<Feature> feature = features.get().getFeature(command.getFeatureId());

            if (feature.isPresent()) {
                final ThingModifiedEvent eventToPersist;
                final ThingModifyCommandResponse response;

                if (feature.get().getDefinition().isPresent()) {
                    eventToPersist = FeatureDefinitionModified.of(command.getId(), command.getFeatureId(),
                            command.getDefinition(), context.nextRevision(), eventTimestamp(), dittoHeaders);
                    response = ModifyFeatureDefinitionResponse.modified(context.getThingId(), command.getFeatureId(),
                            dittoHeaders);
                } else {
                    eventToPersist = FeatureDefinitionCreated.of(command.getId(), command.getFeatureId(),
                            command.getDefinition(), context.nextRevision(), eventTimestamp(), dittoHeaders);
                    response = ModifyFeatureDefinitionResponse.created(context.getThingId(), command.getFeatureId(),
                            command.getDefinition(), dittoHeaders);
                }

                result = ImmutableResult.of(eventToPersist, response);
            } else {
                final DittoRuntimeException exception =
                        featureNotFound(context.getThingId(), command.getFeatureId(), command.getDittoHeaders());
                result = ImmutableResult.of(exception);
            }
        } else {
            final DittoRuntimeException exception =
                    featureNotFound(context.getThingId(), command.getFeatureId(), command.getDittoHeaders());
            result = ImmutableResult.of(exception);
        }

        return result;
    }

}
