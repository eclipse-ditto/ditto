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

import static org.eclipse.ditto.services.things.persistence.actors.strategies.commands.ResultFactory.newResult;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;

/**
 * This strategy handles the {@link DeleteAttributes} command.
 */
@ThreadSafe
public final class DeleteAttributesStrategy extends AbstractCommandStrategy<DeleteAttributes> {

    /**
     * Constructs a new {@code DeleteAttributesStrategy} object.
     */
    DeleteAttributesStrategy() {
        super(DeleteAttributes.class);
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final DeleteAttributes command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final long nextRevision = context.getNextRevision();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        if (thing.getAttributes().isPresent()) {
            final AttributesDeleted attributesDeleted = AttributesDeleted.of(command.getThingId(), nextRevision,
                    eventTimestamp(), dittoHeaders);
            return newResult(attributesDeleted, DeleteAttributesResponse.of(thingId, dittoHeaders));
        } else {
            return newResult(attributesNotFound(thingId, dittoHeaders));
        }
    }
}