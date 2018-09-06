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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureModified;

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
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyFeature command) {

        final Thing nonNullThing = getThingOrThrow(thing);
        ThingCommandSizeValidator.getInstance().ensureValidSize(() -> {
            final long lengthWithOutFeature = nonNullThing.removeFeature(command.getFeatureId())
                    .toJsonString()
                    .length();
            final long featureLength = command.getFeature().toJsonString().length()
                    + command.getFeatureId().length() + 5L;
            return lengthWithOutFeature + featureLength;
        }, command::getDittoHeaders);

        return extractFeature(command, nonNullThing)
                .map(feature -> getModifyResult(context, nextRevision, command))
                .orElseGet(() -> getCreateResult(context, nextRevision, command));
    }

    private Optional<Feature> extractFeature(final ModifyFeature command, final Thing thing) {
        return getThingOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result getModifyResult(final Context context, final long nextRevision, final ModifyFeature command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newMutationResult(command,
                FeatureModified.of(command.getId(), command.getFeature(), nextRevision, getEventTimestamp(),
                        dittoHeaders),
                ModifyFeatureResponse.modified(context.getThingId(), command.getFeatureId(), dittoHeaders), this);
    }

    private Result getCreateResult(final Context context, final long nextRevision, final ModifyFeature command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Feature feature = command.getFeature();

        return ResultFactory.newMutationResult(command,
                FeatureCreated.of(command.getId(), feature, nextRevision, getEventTimestamp(),
                        dittoHeaders),
                ModifyFeatureResponse.created(context.getThingId(), feature, dittoHeaders), this);
    }


    @Override
    public Optional<Feature> determineETagEntity(final ModifyFeature command, @Nullable final Thing thing) {
        return extractFeature(command, getThingOrThrow(thing));
    }
}
