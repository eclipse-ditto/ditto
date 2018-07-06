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

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures} command.
 */
@NotThreadSafe
final class DeleteFeaturesStrategy extends AbstractCommandStrategy<DeleteFeatures> {

    /**
     * Constructs a new {@code DeleteFeaturesStrategy} object.
     */
    DeleteFeaturesStrategy() {
        super(DeleteFeatures.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final DeleteFeatures command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final CommandStrategy.Result result;

        if (context.getThing().getFeatures().isPresent()) {
            final ThingModifiedEvent eventToPersist =
                    FeaturesDeleted.of(context.getThingId(), context.getNextRevision(), eventTimestamp(), dittoHeaders);
            final ThingModifyCommandResponse response = DeleteFeaturesResponse.of(context.getThingId(), dittoHeaders);
            result = ImmutableResult.of(eventToPersist, response);
        } else {
            final DittoRuntimeException exception = featuresNotFound(context.getThingId(), dittoHeaders);
            result = ImmutableResult.of(exception);
        }

        return result;
    }

}
