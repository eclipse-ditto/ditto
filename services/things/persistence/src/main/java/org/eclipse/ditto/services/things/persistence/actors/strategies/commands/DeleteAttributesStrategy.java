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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;

/**
 * This strategy handles the {@link DeleteAttributes} command.
 */
@Immutable
final class DeleteAttributesStrategy extends AbstractCommandStrategy<DeleteAttributes> {

    /**
     * Constructs a new {@code DeleteAttributesStrategy} object.
     */
    DeleteAttributesStrategy() {
        super(DeleteAttributes.class);
    }

    @Override
    protected Result doApply(final Context context, final DeleteAttributes command) {
        final Thing thing = context.getThingOrThrow();

        return thing.getAttributes()
                .map(attributes -> getDeleteAttributesResult(context, command))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.attributesNotFound(context.getThingId(), command.getDittoHeaders())));
    }

    private static Result getDeleteAttributesResult(final Context context, final DeleteAttributes command) {
        final String thingId = context.getThingId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newResult(
                AttributesDeleted.of(thingId, context.getNextRevision(), getEventTimestamp(), dittoHeaders),
                DeleteAttributesResponse.of(thingId, dittoHeaders));
    }

}