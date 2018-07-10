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
@ThreadSafe
final class ModifyFeatureDefinitionStrategy extends AbstractCommandStrategy<ModifyFeatureDefinition> {

    /**
     * Constructs a new {@code ModifyFeatureDefinitionStrategy} object.
     */
    ModifyFeatureDefinitionStrategy() {
        super(ModifyFeatureDefinition.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context,
            final ModifyFeatureDefinition command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final CommandStrategy.Result result;

        final Optional<Features> features = context.getThing().getFeatures();
        if (features.isPresent()) {
            final Optional<Feature> feature = features.get().getFeature(command.getFeatureId());

            if (feature.isPresent()) {
                final ThingModifiedEvent eventToPersist;
                final ThingModifyCommandResponse response;

                if (feature.get().getDefinition().isPresent()) {
                    eventToPersist = FeatureDefinitionModified.of(command.getId(), command.getFeatureId(),
                            command.getDefinition(), context.getNextRevision(), eventTimestamp(), dittoHeaders);
                    response = ModifyFeatureDefinitionResponse.modified(context.getThingId(), command.getFeatureId(),
                            dittoHeaders);
                } else {
                    eventToPersist = FeatureDefinitionCreated.of(command.getId(), command.getFeatureId(),
                            command.getDefinition(), context.getNextRevision(), eventTimestamp(), dittoHeaders);
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
