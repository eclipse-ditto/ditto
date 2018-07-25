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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributeResponse;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;

/**
 * This strategy handles the {@link DeleteAttribute} command.
 */
@Immutable
final class DeleteAttributeStrategy extends AbstractCommandStrategy<DeleteAttribute> {

    /**
     * Constructs a new {@code DeleteAttributeStrategy} object.
     */
    DeleteAttributeStrategy() {
        super(DeleteAttribute.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final DeleteAttribute command) {
        final JsonPointer attrPointer = command.getAttributePointer();

        return getThingOrThrow(thing).getAttributes()
                .filter(attributes -> attributes.contains(attrPointer))
                .map(attributes -> getDeleteAttributeResult(context, nextRevision, command))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.attributeNotFound(context.getThingId(), attrPointer,
                                command.getDittoHeaders())));
    }

    private static Result getDeleteAttributeResult(final Context context, final long nextRevision,
            final DeleteAttribute command) {
        final String thingId = context.getThingId();
        final JsonPointer attrPointer = command.getAttributePointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newResult(
                AttributeDeleted.of(thingId, attrPointer, nextRevision, getEventTimestamp(), dittoHeaders),
                DeleteAttributeResponse.of(thingId, attrPointer, dittoHeaders));
    }

}
