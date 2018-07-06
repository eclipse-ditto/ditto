/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

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
final class ModifyFeaturesStrategy extends AbstractCommandStrategy<ModifyFeatures> {

    /**
     * Constructs a new {@code ModifyFeaturesStrategy} object.
     */
    ModifyFeaturesStrategy() {
        super(ModifyFeatures.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final ModifyFeatures command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final ThingModifiedEvent eventToPersist;
        final ThingModifyCommandResponse response;

        if (context.getThing().getFeatures().isPresent()) {
            eventToPersist = FeaturesModified.of(command.getId(), command.getFeatures(), context.getNextRevision(),
                    eventTimestamp(), dittoHeaders);
            response = ModifyFeaturesResponse.modified(context.getThingId(), dittoHeaders);
        } else {
            eventToPersist = FeaturesCreated.of(command.getId(), command.getFeatures(), context.getNextRevision(),
                    eventTimestamp(), dittoHeaders);
            response = ModifyFeaturesResponse.created(context.getThingId(), command.getFeatures(), dittoHeaders);
        }

        return ImmutableResult.of(eventToPersist, response);
    }

}
