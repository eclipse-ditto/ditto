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
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureResponse;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeature} command.
 */
@Immutable
final class DeleteFeatureStrategy extends AbstractConditionalHeadersCheckingCommandStrategy<DeleteFeature, Feature> {

    /**
     * Constructs a new {@code DeleteFeatureStrategy} object.
     */
    DeleteFeatureStrategy() {
        super(DeleteFeature.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final DeleteFeature command) {
        final String featureId = command.getFeatureId();

        return extractFeature(command, thing)
                .map(feature -> getDeleteFeatureResult(context, nextRevision, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.featureNotFound(context.getState(), featureId,
                                command.getDittoHeaders())));
    }

    private Optional<Feature> extractFeature(final DeleteFeature command, @Nullable final Thing thing) {
        final String featureId = command.getFeatureId();

        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(featureId));
    }

    private Result<ThingEvent> getDeleteFeatureResult(final Context<ThingId> context, final long nextRevision,
            final DeleteFeature command, @Nullable final Thing thing) {
        final ThingId thingId = context.getState();
        final String featureId = command.getFeatureId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final ThingEvent event =
                FeatureDeleted.of(thingId, featureId, nextRevision, getEventTimestamp(), dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeleteFeatureResponse.of(thingId, featureId, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<Feature> determineETagEntity(final DeleteFeature command, @Nullable final Thing thing) {
        return Optional.empty();
    }
}
