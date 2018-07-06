/* Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures} command.
 */
@NotThreadSafe
final class ModifyFeaturesStrategy extends AbstractThingCommandStrategy<ModifyFeatures> {

    /**
     * Constructs a new {@code ModifyFeaturesStrategy} object.
     */
    public ModifyFeaturesStrategy() {
        super(ModifyFeatures.class);
    }

    @Override
    protected Result doApply(final Context context, final ModifyFeatures command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final ThingModifiedEvent eventToPersist;
        final ThingModifyCommandResponse response;

        if (context.getThing().getFeatures().isPresent()) {
            eventToPersist = FeaturesModified.of(command.getId(), command.getFeatures(), context.nextRevision(),
                    eventTimestamp(), dittoHeaders);
            response = ModifyFeaturesResponse.modified(context.getThingId(), dittoHeaders);
        } else {
            eventToPersist = FeaturesCreated.of(command.getId(), command.getFeatures(), context.nextRevision(),
                    eventTimestamp(), dittoHeaders);
            response = ModifyFeaturesResponse.created(context.getThingId(), command.getFeatures(), dittoHeaders);
        }

        return ImmutableResult.of(eventToPersist, response);
    }

}
