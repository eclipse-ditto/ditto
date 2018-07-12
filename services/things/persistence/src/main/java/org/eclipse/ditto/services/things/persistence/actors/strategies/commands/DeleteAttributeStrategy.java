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
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final DeleteAttribute command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThingOrThrow();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final JsonPointer attributeJsonPointer = command.getAttributePointer();

        return thing.getAttributes()
                .filter(attributes -> attributes.contains(attributeJsonPointer))
                .map(attributes -> AttributeDeleted.of(thingId, attributeJsonPointer, context.getNextRevision(),
                        getEventTimestamp(), dittoHeaders))
                .map(attributeDeleted -> ResultFactory.newResult(attributeDeleted,
                        DeleteAttributeResponse.of(thingId, attributeJsonPointer, dittoHeaders)))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.attributeNotFound(thingId, attributeJsonPointer, dittoHeaders)));
    }

}