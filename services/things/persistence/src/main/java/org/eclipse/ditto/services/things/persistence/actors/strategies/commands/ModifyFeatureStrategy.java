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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.ModifyFeature} command.
 */
@Immutable
final class ModifyFeatureStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<ModifyFeature, Feature> {

    /**
     * Constructs a new {@code ModifyFeatureStrategy} object.
     */
    ModifyFeatureStrategy() {
        super(ModifyFeature.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final ModifyFeature command) {

        final Thing nonNullThing = getEntityOrThrow(thing);
        ThingCommandSizeValidator.getInstance().ensureValidSize(() -> {
            final long lengthWithOutFeature = nonNullThing.removeFeature(command.getFeatureId())
                    .toJsonString()
                    .length();
            final long featureLength = command.getFeature().toJsonString().length()
                    + command.getFeatureId().length() + 5L;
            return lengthWithOutFeature + featureLength;
        }, command::getDittoHeaders);

        return extractFeature(command, nonNullThing)
                .map(feature -> getModifyResult(context, nextRevision, command, thing))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing));
    }

    private Optional<Feature> extractFeature(final ModifyFeature command, final Thing thing) {
        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeature command, @Nullable final Thing thing) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent event =
                FeatureModified.of(command.getThingEntityId(), command.getFeature(), nextRevision, getEventTimestamp(),
                        dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeatureResponse.modified(context.getEntityId(), command.getFeatureId(), dittoHeaders),
                thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeature command, @Nullable final Thing thing) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Feature feature = command.getFeature();

        final ThingEvent event =
                FeatureCreated.of(command.getThingEntityId(), feature, nextRevision, getEventTimestamp(), dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeatureResponse.created(context.getEntityId(), feature, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }


    @Override
    public Optional<Feature> determineETagEntity(final ModifyFeature command, @Nullable final Thing thing) {
        return Optional.of(command.getFeature());
    }
}
