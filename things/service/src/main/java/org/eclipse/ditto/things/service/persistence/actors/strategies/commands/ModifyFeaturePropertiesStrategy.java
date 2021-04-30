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
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperties} command.
 */
@Immutable
final class ModifyFeaturePropertiesStrategy extends AbstractThingCommandStrategy<ModifyFeatureProperties> {

    /**
     * Constructs a new {@code ModifyFeaturePropertiesStrategy} object.
     */
    ModifyFeaturePropertiesStrategy() {
        super(ModifyFeatureProperties.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyFeatureProperties command,
            @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();

        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingWithoutProperties = nonNullThing.removeFeatureProperties(featureId).toJson();
        final JsonObject propertiesJsonObject = command.getProperties().toJson();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                () -> {
                    final long lengthWithOutProperties = thingWithoutProperties.getUpperBoundForStringSize();
                    final long propertiesLength = propertiesJsonObject.getUpperBoundForStringSize()
                            + "properties".length() + featureId.length() + 5L;
                    return lengthWithOutProperties + propertiesLength;
                },
                () -> {
                    final long lengthWithOutProperties = thingWithoutProperties.toString().length();
                    final long propertiesLength = propertiesJsonObject.toString().length()
                            + "properties".length() + featureId.length() + 5L;
                    return lengthWithOutProperties + propertiesLength;
                },
                command::getDittoHeaders);

        return extractFeature(command, nonNullThing)
                .map(feature -> getModifyOrCreateResult(feature, context, nextRevision, command, thing, metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), featureId,
                                command.getDittoHeaders()), command));
    }

    private Optional<Feature> extractFeature(final ModifyFeatureProperties command, @Nullable final Thing thing) {
        return Optional.ofNullable(thing)
                .flatMap(Thing::getFeatures)
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getModifyOrCreateResult(final Feature feature, final Context<ThingId> context,
            final long nextRevision, final ModifyFeatureProperties command, @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        return feature.getProperties()
                .map(properties -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatureProperties command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                FeaturePropertiesModified.of(thingId, featureId, command.getProperties(), nextRevision,
                        getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeaturePropertiesResponse.modified(context.getState(), featureId, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatureProperties command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final String featureId = command.getFeatureId();
        final FeatureProperties featureProperties = command.getProperties();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event = FeaturePropertiesCreated.of(thingId, featureId, featureProperties, nextRevision,
                getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeaturePropertiesResponse.created(thingId, featureId, featureProperties, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }


    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyFeatureProperties command,
            @Nullable final Thing previousEntity) {
        return extractFeature(command, previousEntity).flatMap(Feature::getProperties).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyFeatureProperties command, @Nullable final Thing newEntity) {
        return Optional.of(command.getProperties()).flatMap(EntityTag::fromEntity);
    }
}
