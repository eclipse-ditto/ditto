/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.ModifyFeature} command.
 */
@NotThreadSafe
final class ModifyFeatureStrategy extends AbstractThingCommandStrategy<ModifyFeature> {

    /**
     * Constructs a new {@code ModifyFeatureStrategy} object.
     */
    public ModifyFeatureStrategy() {
        super(ModifyFeature.class);
    }

    @Override
    protected Result doApply(final Context context, final ModifyFeature command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final ThingModifiedEvent eventToPersist;
        final ThingModifyCommandResponse response;

        final Optional<Features> features = context.getThing().getFeatures();
        if (features.isPresent() && features.get().getFeature(command.getFeatureId()).isPresent()) {
            eventToPersist = FeatureModified.of(command.getId(), command.getFeature(), context.nextRevision(),
                    eventTimestamp(), dittoHeaders);
            response = ModifyFeatureResponse.modified(context.getThingId(), command.getFeatureId(), dittoHeaders);
        } else {
            eventToPersist = FeatureCreated.of(command.getId(), command.getFeature(), context.nextRevision(),
                    eventTimestamp(), dittoHeaders);
            response = ModifyFeatureResponse.created(context.getThingId(), command.getFeature(), dittoHeaders);
        }

        return ImmutableResult.of(eventToPersist, response);
    }

}
