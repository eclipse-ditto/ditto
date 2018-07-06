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
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty} command.
 */
@NotThreadSafe
final class ModifyFeaturePropertyStrategy extends AbstractThingCommandStrategy<ModifyFeatureProperty> {

    /**
     * Constructs a new {@code ModifyFeaturePropertyStrategy} object.
     */
    public ModifyFeaturePropertyStrategy() {
        super(ModifyFeatureProperty.class);
    }

    @Override
    protected Result doApply(final Context context, final ModifyFeatureProperty command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Result result;

        final Optional<Features> features = context.getThing().getFeatures();
        final String featureId = command.getFeatureId();
        if (features.isPresent()) {
            final Optional<Feature> feature = features.get().getFeature(featureId);

            if (feature.isPresent()) {
                final Optional<FeatureProperties> optionalProperties = feature.get().getProperties();
                final ThingModifiedEvent eventToPersist;
                final ThingModifyCommandResponse response;

                final JsonPointer propertyJsonPointer = command.getPropertyPointer();
                final JsonValue propertyValue = command.getPropertyValue();
                if (optionalProperties.isPresent() && optionalProperties.get().contains(propertyJsonPointer)) {
                    eventToPersist = FeaturePropertyModified.of(command.getId(), featureId, propertyJsonPointer,
                            propertyValue, context.nextRevision(), eventTimestamp(), dittoHeaders);
                    response =
                            ModifyFeaturePropertyResponse.modified(context.getThingId(), featureId, propertyJsonPointer,
                                    dittoHeaders);
                } else {
                    eventToPersist = FeaturePropertyCreated.of(command.getId(), featureId, propertyJsonPointer,
                            propertyValue, context.nextRevision(), eventTimestamp(), dittoHeaders);
                    response =
                            ModifyFeaturePropertyResponse.created(context.getThingId(), featureId, propertyJsonPointer,
                                    propertyValue, dittoHeaders);
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
