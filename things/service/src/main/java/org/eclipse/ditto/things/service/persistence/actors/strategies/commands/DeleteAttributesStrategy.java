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
import java.util.concurrent.CompletableFuture;
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
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributesResponse;
import org.eclipse.ditto.things.model.signals.events.AttributesDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link DeleteAttributes} command.
 */
@Immutable
final class DeleteAttributesStrategy extends AbstractThingModifyCommandStrategy<DeleteAttributes> {

    /**
     * Constructs a new {@code DeleteAttributesStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    DeleteAttributesStrategy(final ActorSystem actorSystem) {
        super(DeleteAttributes.class, actorSystem);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final DeleteAttributes command,
            @Nullable final Metadata metadata) {

        return extractAttributes(thing)
                .map(attributes -> getDeleteAttributesResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.attributesNotFound(context.getState(), command.getDittoHeaders()), command));
    }

    @Override
    protected CompletionStage<DeleteAttributes> performWotValidation(final DeleteAttributes command,
            @Nullable final Thing thing
    ) {
        // TODO TJ validate if allowed
        return CompletableFuture.completedFuture(command);
    }

    private Optional<Attributes> extractAttributes(final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getAttributes();
    }

    private Result<ThingEvent<?>> getDeleteAttributesResult(final Context<ThingId> context, final long nextRevision,
            final DeleteAttributes command, @Nullable final Thing thing, @Nullable final Metadata metadata) {
        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeleteAttributesResponse.of(thingId, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command,
                AttributesDeleted.of(thingId, nextRevision, getEventTimestamp(), dittoHeaders, metadata), response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteAttributes command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(Thing::getAttributes).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteAttributes command, @Nullable final Thing newEntity) {
        return Optional.empty();
    }
}
