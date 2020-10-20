/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredPropertyResponse;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertyCreated;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertyModified;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredProperty} command.
 */
@Immutable
final class ModifyFeatureDesiredPropertyStrategy extends AbstractThingCommandStrategy<ModifyFeatureDesiredProperty> {

    /**
     * Constructs a new {@code ModifyFeatureDesiredPropertyStrategy} object.
     */
    ModifyFeatureDesiredPropertyStrategy() {
        super(ModifyFeatureDesiredProperty.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyFeatureDesiredProperty command,
            @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();
        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingWithoutFeatureDesiredPropertyJsonObject =
                nonNullThing.removeFeatureDesiredProperty(featureId, command.getDesiredPropertyPointer()).toJson();
        final JsonValue propertyValue = command.getDesiredPropertyValue();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                () -> {
                    final long lengthWithOutProperty =
                            thingWithoutFeatureDesiredPropertyJsonObject.getUpperBoundForStringSize();
                    final long propertyLength =
                            propertyValue.getUpperBoundForStringSize() + command.getDesiredPropertyPointer().length() +
                                    5L;
                    return lengthWithOutProperty + propertyLength;
                },
                () -> {
                    final long lengthWithOutProperty = thingWithoutFeatureDesiredPropertyJsonObject.toString().length();
                    final long propertyLength =
                            propertyValue.toString().length() + command.getDesiredPropertyPointer().length() + 5L;
                    return lengthWithOutProperty + propertyLength;
                },
                command::getDittoHeaders);

        return extractFeature(command, nonNullThing)
                .map(feature -> getModifyOrCreateResult(feature, context, nextRevision, command, thing, metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), featureId,
                                command.getDittoHeaders()), command));
    }

    private Optional<Feature> extractFeature(final ModifyFeatureDesiredProperty command, @Nullable final Thing thing) {
        return Optional.ofNullable(thing)
                .flatMap(Thing::getFeatures)
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent> getModifyOrCreateResult(final Feature feature, final Context<ThingId> context,
            final long nextRevision, final ModifyFeatureDesiredProperty command, @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        return feature.getDesiredProperties()
                .filter(desiredProperties -> desiredProperties.contains(command.getDesiredPropertyPointer()))
                .map(featureProperties -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    private Result<ThingEvent> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatureDesiredProperty command, @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();
        final JsonPointer propertyPointer = command.getDesiredPropertyPointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                FeatureDesiredPropertyModified.of(command.getThingEntityId(), featureId, propertyPointer,
                        command.getDesiredPropertyValue(), nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders<?> response = appendETagHeaderIfProvided(command,
                ModifyFeatureDesiredPropertyResponse.modified(context.getState(), featureId, propertyPointer,
                        dittoHeaders),
                thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatureDesiredProperty command, @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();
        final JsonPointer propertyPointer = command.getDesiredPropertyPointer();
        final JsonValue propertyValue = command.getDesiredPropertyValue();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                FeatureDesiredPropertyCreated.of(command.getThingEntityId(), featureId, propertyPointer, propertyValue,
                        nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders<?> response = appendETagHeaderIfProvided(command,
                ModifyFeatureDesiredPropertyResponse.created(context.getState(), featureId, propertyPointer,
                        propertyValue, dittoHeaders),
                thing);

        return ResultFactory.newMutationResult(command, event, response);
    }


    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyFeatureDesiredProperty command,
            @Nullable final Thing previousEntity) {
        return extractFeature(command, previousEntity).flatMap(Feature::getDesiredProperties)
                .flatMap(props -> props.getValue(command.getDesiredPropertyPointer()).flatMap(EntityTag::fromEntity));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyFeatureDesiredProperty command,
            @Nullable final Thing newEntity) {
        return Optional.of(command.getDesiredPropertyValue()).flatMap(EntityTag::fromEntity);
    }
}
