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
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.things.model.signals.events.FeaturesCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturesModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures} command.
 */
@Immutable
final class ModifyFeaturesStrategy extends AbstractThingCommandStrategy<ModifyFeatures> {

    /**
     * Constructs a new {@code ModifyFeaturesStrategy} object.
     */
    ModifyFeaturesStrategy() {
        super(ModifyFeatures.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyFeatures command,
            @Nullable final Metadata metadata) {

        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingWithoutFeaturesJsonObject = nonNullThing.removeFeatures().toJson();
        final JsonObject featuresJsonObject = command.getFeatures().toJson();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                () -> {
                    final long lengthWithOutFeatures = thingWithoutFeaturesJsonObject.getUpperBoundForStringSize();
                    final long featuresLength = featuresJsonObject.getUpperBoundForStringSize() + "features".length() + 5L;
                    return lengthWithOutFeatures + featuresLength;
                },
                () -> {
                    final long lengthWithOutFeatures = thingWithoutFeaturesJsonObject.toString().length();
                    final long featuresLength = featuresJsonObject.toString().length() + "features".length() + 5L;
                    return lengthWithOutFeatures + featuresLength;
                },
                command::getDittoHeaders);

        return nonNullThing.getFeatures()
                .map(features -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatures command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                FeaturesModified.of(command.getEntityId(), command.getFeatures(), nextRevision,
                        getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeaturesResponse.modified(context.getState(), dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatures command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final Features features = command.getFeatures();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                FeaturesCreated.of(command.getEntityId(), features, nextRevision, getEventTimestamp(),
                        dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeaturesResponse.created(context.getState(), features, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }


    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyFeatures command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(Thing::getFeatures).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyFeatures command, @Nullable final Thing newEntity) {
        return Optional.of(command.getFeatures()).flatMap(EntityTag::fromEntity);
    }
}
