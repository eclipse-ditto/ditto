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
@ThreadSafe
final class ModifyFeaturePropertiesStrategy extends AbstractCommandStrategy<ModifyFeatureProperties> {

    /**
     * Constructs a new {@code ModifyFeaturePropertiesStrategy} object.
     */
    ModifyFeaturePropertiesStrategy() {
        super(ModifyFeatureProperties.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context,
            final ModifyFeatureProperties command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final CommandStrategy.Result result;

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
                            context.getNextRevision(), eventTimestamp(), dittoHeaders);
                    response = ModifyFeaturePropertiesResponse.modified(context.getThingId(), featureId, dittoHeaders);
                } else {
                    eventToPersist = FeaturePropertiesCreated.of(command.getId(), featureId, featureProperties,
                            context.getNextRevision(), eventTimestamp(), dittoHeaders);
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
