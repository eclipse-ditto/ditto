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

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDefinition} command.
 */
@Immutable
final class ModifyFeatureDefinitionStrategy extends AbstractThingCommandStrategy<ModifyFeatureDefinition> {

    /**
     * Constructs a new {@code ModifyFeatureDefinitionStrategy} object.
     */
    ModifyFeatureDefinitionStrategy() {
        super(ModifyFeatureDefinition.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyFeatureDefinition command,
            @Nullable final Metadata metadata) {

        final String featureId = command.getFeatureId();

        return extractFeature(command, thing)
                .map(feature -> getModifyOrCreateResult(feature, context, nextRevision, command, thing, metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), featureId,
                                command.getDittoHeaders()), command));
    }

    private Optional<Feature> extractFeature(final ModifyFeatureDefinition command, final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getModifyOrCreateResult(final Feature feature, final Context<ThingId> context,
            final long nextRevision, final ModifyFeatureDefinition command, @Nullable final Thing thing,
            @Nullable final Metadata metadata) {

        return feature.getDefinition()
                .map(definition -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatureDefinition command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final String featureId = command.getFeatureId();

        final ThingEvent<?> event =
                FeatureDefinitionModified.of(thingId, featureId, command.getDefinition(), nextRevision,
                        getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeatureDefinitionResponse.modified(thingId, featureId, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatureDefinition command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final String featureId = command.getFeatureId();

        final ThingEvent<?> event = FeatureDefinitionCreated.of(thingId, featureId, command.getDefinition(),
                nextRevision, getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeatureDefinitionResponse.created(thingId, featureId, command.getDefinition(), dittoHeaders),
                thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyFeatureDefinition command,
            @Nullable final Thing previousEntity) {
        return extractFeature(command, previousEntity).flatMap(Feature::getDefinition).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyFeatureDefinition command, @Nullable final Thing newEntity) {
        return Optional.of(command.getDefinition()).flatMap(EntityTag::fromEntity);
    }
}
