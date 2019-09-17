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
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures} command.
 */
@Immutable
final class DeleteFeaturesStrategy extends AbstractConditionalHeadersCheckingCommandStrategy<DeleteFeatures, Features> {

    /**
     * Constructs a new {@code DeleteFeaturesStrategy} object.
     */
    DeleteFeaturesStrategy() {
        super(DeleteFeatures.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final DeleteFeatures command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return extractFeatures(thing)
                .map(features -> ResultFactory.newMutationResult(command, getEventToPersist(context, nextRevision, dittoHeaders),
                        getResponse(context, dittoHeaders), this))
                .orElseGet(() -> ResultFactory.newErrorResult(ExceptionFactory.featuresNotFound(context.getThingEntityId(),
                        dittoHeaders)));
    }

    private Optional<Features> extractFeatures(final @Nullable Thing thing) {
        return getThingOrThrow(thing).getFeatures();
    }

    private static ThingModifiedEvent getEventToPersist(final Context context, final long nextRevision,
            final DittoHeaders dittoHeaders) {
        return FeaturesDeleted.of(context.getThingEntityId(), nextRevision, getEventTimestamp(), dittoHeaders);
    }

    private static ThingModifyCommandResponse getResponse(final Context context, final DittoHeaders dittoHeaders) {
        return DeleteFeaturesResponse.of(context.getThingEntityId(), dittoHeaders);
    }

    @Override
    public Optional<Features> determineETagEntity(final DeleteFeatures command, @Nullable final Thing thing) {
        return extractFeatures(thing);
    }
}
