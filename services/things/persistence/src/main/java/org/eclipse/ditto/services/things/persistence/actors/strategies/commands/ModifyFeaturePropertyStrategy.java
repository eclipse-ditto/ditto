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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty} command.
 */
@Immutable
final class ModifyFeaturePropertyStrategy extends AbstractCommandStrategy<ModifyFeatureProperty> {

    /**
     * Constructs a new {@code ModifyFeaturePropertyStrategy} object.
     */
    ModifyFeaturePropertyStrategy() {
        super(ModifyFeatureProperty.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyFeatureProperty command) {
        final String featureId = command.getFeatureId();

        return getThingOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .map(feature -> getModifyOrCreateResult(feature, context, nextRevision, command))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.featureNotFound(context.getThingId(), featureId, command.getDittoHeaders())));
    }

    private static Result getModifyOrCreateResult(final Feature feature, final Context context,
            final long nextRevision, final ModifyFeatureProperty command) {

        return feature.getProperties()
                .filter(featureProperties -> featureProperties.contains(command.getPropertyPointer()))
                .map(featureProperties -> getModifyResult(context, nextRevision, command))
                .orElseGet(() -> getCreateResult(context, nextRevision, command));
    }

    private static Result getModifyResult(final Context context, final long nextRevision,
            final ModifyFeatureProperty command) {
        final String featureId = command.getFeatureId();
        final JsonPointer propertyPointer = command.getPropertyPointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newResult(
                FeaturePropertyModified.of(command.getId(), featureId, propertyPointer, command.getPropertyValue(),
                        nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyFeaturePropertyResponse.modified(context.getThingId(), featureId, propertyPointer, dittoHeaders));
    }

    private static Result getCreateResult(final Context context, final long nextRevision,
            final ModifyFeatureProperty command) {
        final String featureId = command.getFeatureId();
        final JsonPointer propertyPointer = command.getPropertyPointer();
        final JsonValue propertyValue = command.getPropertyValue();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newResult(
                FeaturePropertyCreated.of(command.getId(), featureId, propertyPointer, propertyValue,
                        nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyFeaturePropertyResponse.created(context.getThingId(), featureId, propertyPointer, propertyValue,
                        dittoHeaders));
    }

}
