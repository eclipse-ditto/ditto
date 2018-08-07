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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute} command.
 */
@Immutable
final class RetrieveAttributeStrategy extends AbstractCommandStrategy<RetrieveAttribute> {

    /**
     * Constructs a new {@code RetrieveAttributeStrategy} object.
     */
    RetrieveAttributeStrategy() {
        super(RetrieveAttribute.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveAttribute command) {

        return getThingOrThrow(thing).getAttributes()
                .map(attributes -> getAttributeValueResult(attributes, context.getThingId(), command))
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.attributesNotFound(context.getThingId(), command.getDittoHeaders())));
    }

    private static Result getAttributeValueResult(final JsonObject attributes, final String thingId,
            final RetrieveAttribute command) {

        final JsonPointer attributePointer = command.getAttributePointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return attributes.getValue(attributePointer)
                .map(value -> RetrieveAttributeResponse.of(thingId, attributePointer, value, dittoHeaders))
                .map(ResultFactory::newResult)
                .orElseGet(() -> ResultFactory.newResult(
                        ExceptionFactory.attributeNotFound(thingId, attributePointer, dittoHeaders)));
    }

}
