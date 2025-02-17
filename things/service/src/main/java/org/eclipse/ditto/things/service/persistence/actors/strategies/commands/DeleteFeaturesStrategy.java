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
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.things.model.signals.events.FeaturesDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatures} command.
 */
@Immutable
final class DeleteFeaturesStrategy extends AbstractThingModifyCommandStrategy<DeleteFeatures> {

    /**
     * Constructs a new {@code DeleteFeaturesStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    DeleteFeaturesStrategy(final ActorSystem actorSystem) {
        super(DeleteFeatures.class, actorSystem);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final DeleteFeatures command,
            @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return extractFeatures(thing)
                .map(features -> {
                            final CompletionStage<DeleteFeatures> validatedStage = buildValidatedStage(command, thing);
                            final CompletionStage<ThingEvent<?>> eventStage = validatedStage.thenApply(deleteFeatures ->
                                    FeaturesDeleted.of(context.getState(), nextRevision, getEventTimestamp(),
                                            dittoHeaders, metadata)
                            );
                            final CompletionStage<WithDittoHeaders> responseStage = validatedStage
                                    .thenApply(deleteFeatures ->
                                            appendETagHeaderIfProvided(deleteFeatures,
                                                    DeleteFeaturesResponse.of(context.getState(),
                                                            createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                                                    thing)
                                    );

                            return ResultFactory.newMutationResult(command, eventStage, responseStage);
                        }
                )
                .orElseGet(() ->
                        ResultFactory.newErrorResult(ExceptionFactory.featuresNotFound(context.getState(),
                                dittoHeaders), command));
    }

    @Override
    protected CompletionStage<DeleteFeatures> performWotValidation(
            final DeleteFeatures command,
            @Nullable final Thing previousThing,
            @Nullable final Thing previewThing
    ) {
        return wotThingModelValidator.validateThingScopedDeletion(
                Optional.ofNullable(previousThing)
                        .flatMap(Thing::getDefinition)
                        .orElse(null),
                command.getResourcePath(),
                command.getDittoHeaders()
        ).thenApply(aVoid -> command);
    }

    private Optional<Features> extractFeatures(@Nullable final Thing thing) {
        return getEntityOrThrow(thing).getFeatures();
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteFeatures command, @Nullable final Thing previousEntity) {
        return extractFeatures(previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteFeatures command, @Nullable final Thing newEntity) {
        return Optional.empty();
    }
}
