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

import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newErrorResult;
import static org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory.newMutationResult;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
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
import org.eclipse.ditto.wot.integration.provider.WotThingDescriptionProvider;

import akka.actor.ActorSystem;

/**
 * This strategy handles the {@link CreateThingStrategy} command.
 */
@Immutable
final class CreateThingStrategy extends AbstractThingCommandStrategy<CreateThing> {

    private final WotThingDescriptionProvider wotThingDescriptionProvider;

    /**
     * Constructs a new {@link CreateThingStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    CreateThingStrategy(final ActorSystem actorSystem) {
        super(CreateThing.class);
        wotThingDescriptionProvider = WotThingDescriptionProvider.get(actorSystem);
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
            newThing =
                    handleCommandVersion(context, command.getImplementedSchemaVersion(), command.getThing(),
                            commandHeaders);
        } catch (final DittoRuntimeException e) {
            return newErrorResult(e, command);
        }

        // for v2 upwards, set the policy-id to the thing-id if none is specified:
        if (newThing.getPolicyId().isEmpty()) {
            newThing = newThing.setPolicyId(PolicyId.of(context.getState()));
        }

        final Thing finalNewThing = newThing;
        newThing = wotThingDescriptionProvider.provideThingSkeletonForCreation(
                        command.getEntityId(),
                        newThing.getDefinition().orElse(null),
                        commandHeaders
                )
                .map(wotBasedThingSkeleton ->
                        JsonFactory.mergeJsonValues(finalNewThing.toJson(), wotBasedThingSkeleton.toJson())
                )
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newThing)
                .orElse(finalNewThing);

        final Instant now = Instant.now();
        final Thing newThingWithImplicits = newThing.toBuilder()
                .setModified(now)
                .setCreated(now)
                .setRevision(nextRevision)
                .setMetadata(metadata)
                .build();
        final ThingCreated thingCreated = ThingCreated.of(newThingWithImplicits, nextRevision, now, commandHeaders,
                metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                CreateThingResponse.of(newThingWithImplicits, commandHeaders),
                newThingWithImplicits);

        return newMutationResult(command, thingCreated, response, true, false);
    }

    private Thing handleCommandVersion(final Context<ThingId> context, final JsonSchemaVersion version,
            final Thing thing,
            final DittoHeaders dittoHeaders) {

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
