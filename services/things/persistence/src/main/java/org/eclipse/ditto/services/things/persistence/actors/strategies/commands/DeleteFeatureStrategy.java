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

import java.util.Optional;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeature} command.
 */
@ThreadSafe
final class DeleteFeatureStrategy extends AbstractCommandStrategy<DeleteFeature> {

    /**
     * Constructs a new {@code DeleteFeatureStrategy} object.
     */
    DeleteFeatureStrategy() {
        super(DeleteFeature.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final DeleteFeature command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final CommandStrategy.Result result;

        final Optional<String> featureIdOptional = context.getThing().getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()))
                .map(Feature::getId);
        if (featureIdOptional.isPresent()) {
            final ThingModifiedEvent eventToPersist = FeatureDeleted.of(context.getThingId(), featureIdOptional.get(),
                    context.getNextRevision(), eventTimestamp(), dittoHeaders);
            final ThingModifyCommandResponse response =
                    DeleteFeatureResponse.of(context.getThingId(), command.getFeatureId(), dittoHeaders);
            result = ImmutableResult.of(eventToPersist, response);
        } else {
            final DittoRuntimeException exception =
                    featureNotFound(context.getThingId(), command.getFeatureId(), dittoHeaders);
            result = ImmutableResult.of(exception);
        }

        return result;
    }

}
