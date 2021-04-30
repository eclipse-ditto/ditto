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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributeResponse;
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link DeleteAttribute} command.
 */
@Immutable
final class DeleteAttributeStrategy extends AbstractThingCommandStrategy<DeleteAttribute> {

    /**
     * Constructs a new {@code DeleteAttributeStrategy} object.
     */
    DeleteAttributeStrategy() {
        super(DeleteAttribute.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final DeleteAttribute command,
            @Nullable final Metadata metadata) {

        final JsonPointer attrPointer = command.getAttributePointer();

        final Optional<Attributes> attrs = getEntityOrThrow(thing).getAttributes()
                .filter(attributes -> attributes.contains(attrPointer));
        return attrs
                .map(attributes -> getDeleteAttributeResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.attributeNotFound(context.getState(), attrPointer,
                                command.getDittoHeaders()), command));
    }

    private Result<ThingEvent<?>> getDeleteAttributeResult(final Context<ThingId> context, final long nextRevision,
            final DeleteAttribute command, @Nullable final Thing thing, @Nullable final Metadata metadata) {
        final ThingId thingId = context.getState();
        final JsonPointer attrPointer = command.getAttributePointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeleteAttributeResponse.of(thingId, attrPointer, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command,
                AttributeDeleted.of(thingId, attrPointer, nextRevision, getEventTimestamp(), dittoHeaders, metadata),
                response);
    }


    @Override
    public Optional<EntityTag> previousEntityTag(final DeleteAttribute command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity)
                .flatMap(Thing::getAttributes)
                .flatMap(attr -> attr.getValue(command.getAttributePointer()).flatMap(EntityTag::fromEntity));
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final DeleteAttribute command, @Nullable final Thing newEntity) {
        return Optional.empty();
    }
}
