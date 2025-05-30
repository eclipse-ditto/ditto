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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.PolicyIdMissingException;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link CreateThingStrategy} command.
 */
@Immutable
final class CreateThingStrategy extends AbstractThingModifyCommandStrategy<CreateThing> {

    /**
     * Constructs a new {@link CreateThingStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    CreateThingStrategy(final ActorSystem actorSystem) {
        super(CreateThing.class, actorSystem);
    }

    @Override
    protected Optional<Metadata> calculateRelativeMetadata(@Nullable Thing entity, CreateThing command) {
        return super.calculateRelativeMetadata(command.getThing(), command);
    }

    @Override
    public boolean isDefined(final CreateThing command) {
        return true;
    }

    @Override
    public boolean isDefined(final Context<ThingId> context, @Nullable final Thing thing, final CreateThing command) {
        final boolean thingExists = Optional.ofNullable(thing)
                .map(t -> !t.isDeleted())
                .orElse(false);

        return !thingExists && Objects.equals(context.getState(), command.getEntityId());
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final CreateThing command,
            @Nullable final Metadata metadata) {

        final DittoHeaders commandHeaders = command.getDittoHeaders();

        // Thing not yet created - do so ..
        Thing newThing;
        try {
            newThing = handleCommandVersion(context, command.getThing(), commandHeaders);
        } catch (final DittoRuntimeException e) {
            return ResultFactory.newErrorResult(e, command);
        }

        // for v2 upwards, set the policy-id to the thing-id if none is specified:
        if (newThing.getPolicyId().isEmpty()) {
            newThing = newThing.setPolicyId(PolicyId.of(context.getState()));
        }

        final Instant now = Instant.now();

        final Thing finalNewThing = newThing;
        final CompletionStage<Thing> thingStage = wotThingSkeletonGenerator.provideThingSkeletonForCreation(
                        command.getEntityId(),
                        newThing.getDefinition().orElse(null),
                        commandHeaders
                )
                .thenApply(opt -> opt.map(wotBasedThingSkeleton ->
                                        JsonFactory.mergeJsonValues(finalNewThing.toJson(), wotBasedThingSkeleton.toJson())
                                )
                                .filter(JsonValue::isObject)
                                .map(JsonValue::asObject)
                                .map(ThingsModelFactory::newThing)
                                .orElse(finalNewThing)
                ).thenApply(enhancedThing -> enhancedThing.toBuilder()
                        .setModified(now)
                        .setCreated(now)
                        .setRevision(nextRevision)
                        .setMetadata(metadata)
                        .build()
                );

        // validate based on potentially referenced Thing WoT TM/TD
        final CompletionStage<Pair<CreateThing, Thing>> validatedStage =
                thingStage.thenComposeAsync(createdThingWithImplicits ->
                        buildValidatedStage(command, null, createdThingWithImplicits)
                                .thenApplyAsync(createThing ->
                                        new Pair<>(createThing, createdThingWithImplicits), wotValidationExecutor),
                        wotValidationExecutor
                );

        final CompletionStage<ThingEvent<?>> eventStage = validatedStage.thenApply(pair ->
                ThingCreated.of(pair.second(), nextRevision, now, commandHeaders, metadata)
        );

        final CompletionStage<WithDittoHeaders> responseStage = validatedStage.thenApply(pair ->
                appendETagHeaderIfProvided(pair.first(), CreateThingResponse.of(pair.second(),
                                createCommandResponseDittoHeaders(commandHeaders, nextRevision)
                        ),
                        pair.second())
        );

        return ResultFactory.newMutationResult(command, eventStage, responseStage, true, false);
    }

    @Override
    protected CompletionStage<CreateThing> performWotValidation(final CreateThing command,
            @Nullable final Thing previousThing,
            @Nullable final Thing previewThing
    ) {
        return wotThingModelValidator.validateThing(
                Optional.ofNullable(previewThing).orElse(command.getThing()),
                command.getResourcePath(),
                command.getDittoHeaders()
        ).thenApply(aVoid -> command);
    }

    private Thing handleCommandVersion(final Context<ThingId> context,
            final Thing thing,
            final DittoHeaders dittoHeaders
    ) {
        // policyId is required for v2
        if (thing.getPolicyId().isEmpty()) {
            throw PolicyIdMissingException.fromThingIdOnCreate(context.getState(), dittoHeaders);
        }

        return setLifecycleActive(thing);
    }

    private static Thing setLifecycleActive(final Thing thing) {
        if (ThingLifecycle.ACTIVE.equals(thing.getLifecycle().orElse(null))) {
            return thing;
        }
        return ThingsModelFactory.newThingBuilder(thing)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .build();
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final CreateThing command, @Nullable final Thing previousEntity) {
        return Optional.empty();
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final CreateThing command, @Nullable final Thing newEntity) {
        return Optional.ofNullable(newEntity).flatMap(EntityTag::fromEntity);
    }
}
