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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties} command.
 */
@Immutable
final class ModifyFeaturePropertiesStrategy extends AbstractCommandStrategy<ModifyFeatureProperties> {

    /**
     * Constructs a new {@code ModifyFeaturePropertiesStrategy} object.
     */
    ModifyFeaturePropertiesStrategy() {
        super(ModifyFeatureProperties.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyFeatureProperties command) {
        final String featureId = command.getFeatureId();

        final Thing nonNullThing = getThingOrThrow(thing);
        ThingCommandSizeValidator.getInstance().ensureValidSize(() -> {
            final long lengthWithOutProperties = nonNullThing.removeFeatureProperties(command.getFeatureId())
                    .toJsonString()
                    .length();
            final long propertiesLength = command.getProperties().toJsonString().length()
                    + "properties".length() + command.getFeatureId().length() + 5L;
            return lengthWithOutProperties + propertiesLength;
        }, command::getDittoHeaders);

        return nonNullThing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .map(feature -> getModifyOrCreateResult(feature, context, nextRevision, command))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.featureNotFound(context.getThingId(), featureId, command.getDittoHeaders())));
    }

    private static Result getModifyOrCreateResult(final Feature feature, final Context context,
            final long nextRevision, final ModifyFeatureProperties command) {

        return feature.getProperties()
                .map(properties -> getModifyResult(context, nextRevision, command))
                .orElseGet(() -> getCreateResult(context, nextRevision, command));
    }

    private static Result getModifyResult(final Context context, final long nextRevision,
            final ModifyFeatureProperties command) {
        final String thingId = context.getThingId();
        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newResult(FeaturePropertiesModified.of(thingId, featureId, command.getProperties(),
                nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyFeaturePropertiesResponse.modified(context.getThingId(), featureId, dittoHeaders));
    }

    private static Result getCreateResult(final Context context, final long nextRevision,
            final ModifyFeatureProperties command) {
        final String thingId = context.getThingId();
        final String featureId = command.getFeatureId();
        final FeatureProperties featureProperties = command.getProperties();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newResult(
                FeaturePropertiesCreated.of(thingId, featureId, featureProperties, nextRevision,
                        getEventTimestamp(), dittoHeaders),
                ModifyFeaturePropertiesResponse.created(thingId, featureId, featureProperties, dittoHeaders));
    }

}
