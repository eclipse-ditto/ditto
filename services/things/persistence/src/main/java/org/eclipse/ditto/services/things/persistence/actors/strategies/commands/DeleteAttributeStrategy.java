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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributeResponse;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link DeleteAttribute} command.
 */
@Immutable
final class DeleteAttributeStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<DeleteAttribute, JsonValue> {

    /**
     * Constructs a new {@code DeleteAttributeStrategy} object.
     */
    DeleteAttributeStrategy() {
        super(DeleteAttribute.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final DeleteAttribute command) {
        final JsonPointer attrPointer = command.getAttributePointer();

        final Optional<Attributes> attrs = getEntityOrThrow(thing).getAttributes()
                .filter(attributes -> attributes.contains(attrPointer));
        return attrs
                .map(attributes -> getDeleteAttributeResult(context, nextRevision, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.attributeNotFound(context.getEntityId(), attrPointer,
                                command.getDittoHeaders())));
    }

    private Result<ThingEvent> getDeleteAttributeResult(final Context<ThingId> context, final long nextRevision,
            final DeleteAttribute command, @Nullable final Thing thing) {
        final ThingId thingId = context.getEntityId();
        final JsonPointer attrPointer = command.getAttributePointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeleteAttributeResponse.of(thingId, attrPointer, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command,
                AttributeDeleted.of(thingId, attrPointer, nextRevision, getEventTimestamp(), dittoHeaders), response);
    }


    @Override
    public Optional<JsonValue> determineETagEntity(final DeleteAttribute command, @Nullable final Thing thing) {
        return Optional.empty();
    }
}
