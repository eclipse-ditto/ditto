/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties} command.
 */
@NotThreadSafe
final class ModifyFeaturePropertiesStrategy extends AbstractThingCommandStrategy<ModifyFeatureProperties> {

    /**
     * Constructs a new {@code ModifyFeaturePropertiesStrategy} object.
     */
    public ModifyFeaturePropertiesStrategy() {
        super(ModifyFeatureProperties.class);
    }

    @Override
    protected Result doApply(final Context context, final ModifyFeatureProperties command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Result result;

        final Optional<Features> features = context.getThing().getFeatures();
        final String featureId = command.getFeatureId();
        if (features.isPresent()) {
            final Optional<Feature> feature = features.get().getFeature(featureId);

            if (feature.isPresent()) {
                final ThingModifiedEvent eventToPersist;
                final ThingModifyCommandResponse response;

                final FeatureProperties featureProperties = command.getProperties();
                if (feature.get().getProperties().isPresent()) {
                    eventToPersist = FeaturePropertiesModified.of(command.getId(), featureId, featureProperties,
                            context.nextRevision(), eventTimestamp(), dittoHeaders);
                    response = ModifyFeaturePropertiesResponse.modified(context.getThingId(), featureId, dittoHeaders);
                } else {
                    eventToPersist = FeaturePropertiesCreated.of(command.getId(), featureId, featureProperties,
                            context.nextRevision(), eventTimestamp(), dittoHeaders);
                    response =
                            ModifyFeaturePropertiesResponse.created(context.getThingId(), featureId, featureProperties,
                                    dittoHeaders);
                }

                result = ImmutableResult.of(eventToPersist, response);
            } else {
                final DittoRuntimeException exception =
                        featureNotFound(context.getThingId(), featureId, command.getDittoHeaders());
                result = ImmutableResult.of(exception);
            }
        } else {
            final DittoRuntimeException exception =
                    featureNotFound(context.getThingId(), featureId, command.getDittoHeaders());
            result = ImmutableResult.of(exception);
        }

        return result;
    }

}
