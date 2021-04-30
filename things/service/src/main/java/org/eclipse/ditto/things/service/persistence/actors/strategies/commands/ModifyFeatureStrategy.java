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
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureResponse;
import org.eclipse.ditto.things.model.signals.events.FeatureCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature} command.
 */
@Immutable
final class ModifyFeatureStrategy extends AbstractThingCommandStrategy<ModifyFeature> {

    /**
     * Constructs a new {@code ModifyFeatureStrategy} object.
     */
    ModifyFeatureStrategy() {
        super(ModifyFeature.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyFeature command,
            @Nullable final Metadata metadata) {

        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingWithoutFeatureJsonObject = nonNullThing.removeFeature(command.getFeatureId()).toJson();
        final JsonObject featureJsonObject = command.getFeature().toJson();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                () -> {
                    final long lengthWithOutFeature = thingWithoutFeatureJsonObject.getUpperBoundForStringSize();
                    final long featureLength = featureJsonObject.getUpperBoundForStringSize()
                            + command.getFeatureId().length() + 5L;
                    return lengthWithOutFeature + featureLength;
                },
                () -> {
                    final long lengthWithOutFeature = thingWithoutFeatureJsonObject.toString().length();
                    final long featureLength = featureJsonObject.toString().length()
                            + command.getFeatureId().length() + 5L;
                    return lengthWithOutFeature + featureLength;
                },
                command::getDittoHeaders);

        return extractFeature(command, nonNullThing)
                .map(feature -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    private Optional<Feature> extractFeature(final ModifyFeature command, @Nullable final Thing thing) {
        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeature command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                FeatureModified.of(command.getEntityId(), command.getFeature(), nextRevision, getEventTimestamp(),
                        dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeatureResponse.modified(context.getState(), command.getFeatureId(), dittoHeaders),
                thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeature command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Feature feature = command.getFeature();

        final ThingEvent<?> event =
                FeatureCreated.of(command.getEntityId(), feature, nextRevision, getEventTimestamp(), dittoHeaders,
                        metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeatureResponse.created(context.getState(), feature, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyFeature command, @Nullable final Thing previousEntity) {
        return extractFeature(command, previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyFeature command, @Nullable final Thing newEntity) {
        return Optional.of(command.getFeature()).flatMap(EntityTag::fromEntity);
    }
}
