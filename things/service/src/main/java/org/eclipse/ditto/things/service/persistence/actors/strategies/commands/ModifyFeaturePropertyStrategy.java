/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty} command.
 */
@Immutable
final class ModifyFeaturePropertyStrategy extends AbstractThingCommandStrategy<ModifyFeatureProperty> {

    /**
     * Constructs a new {@code ModifyFeaturePropertyStrategy} object.
     */
    ModifyFeaturePropertyStrategy() {
        super(ModifyFeatureProperty.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyFeatureProperty command,
            @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();
        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingWithoutFeaturePropertyJsonObject = nonNullThing.removeFeatureProperty(featureId, command.getPropertyPointer()).toJson();
        final JsonValue propertyValue = command.getPropertyValue();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                () -> {
                    final long lengthWithOutProperty = thingWithoutFeaturePropertyJsonObject.getUpperBoundForStringSize();
                    final long propertyLength = propertyValue.getUpperBoundForStringSize() + command.getPropertyPointer().length() + 5L;
                    return lengthWithOutProperty + propertyLength;
                },
                () -> {
                    final long lengthWithOutProperty = thingWithoutFeaturePropertyJsonObject.toString().length();
                    final long propertyLength = propertyValue.toString().length() + command.getPropertyPointer().length() + 5L;
                    return lengthWithOutProperty + propertyLength;
                },
                command::getDittoHeaders);

        return extractFeature(command, nonNullThing)
                .map(feature -> getModifyOrCreateResult(feature, context, nextRevision, command, thing, metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), featureId,
                                command.getDittoHeaders()), command));
    }

    private Optional<Feature> extractFeature(final ModifyFeatureProperty command, @Nullable final Thing thing) {
        return Optional.ofNullable(thing)
                .flatMap(Thing::getFeatures)
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getModifyOrCreateResult(final Feature feature, final Context<ThingId> context,
            final long nextRevision, final ModifyFeatureProperty command, @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        return feature.getProperties()
                .filter(featureProperties -> featureProperties.contains(command.getPropertyPointer()))
                .map(featureProperties -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatureProperty command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();
        final JsonPointer propertyPointer = command.getPropertyPointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event = FeaturePropertyModified.of(command.getEntityId(), featureId, propertyPointer,
                command.getPropertyValue(), nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeaturePropertyResponse.modified(context.getState(), featureId, propertyPointer,
                        dittoHeaders),
                thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatureProperty command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();
        final JsonPointer propertyPointer = command.getPropertyPointer();
        final JsonValue propertyValue = command.getPropertyValue();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                FeaturePropertyCreated.of(command.getEntityId(), featureId, propertyPointer, propertyValue,
                        nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeaturePropertyResponse.created(context.getState(), featureId, propertyPointer,
                        propertyValue, dittoHeaders),
                thing);

        return ResultFactory.newMutationResult(command, event, response);
    }


    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyFeatureProperty command,
            @Nullable final Thing previousEntity) {
        return extractFeature(command, previousEntity).flatMap(Feature::getProperties)
                .flatMap(props -> props.getValue(command.getPropertyPointer()).flatMap(EntityTag::fromEntity));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyFeatureProperty command, @Nullable final Thing newEntity) {
        return Optional.of(command.getPropertyValue()).flatMap(EntityTag::fromEntity);
    }
}
