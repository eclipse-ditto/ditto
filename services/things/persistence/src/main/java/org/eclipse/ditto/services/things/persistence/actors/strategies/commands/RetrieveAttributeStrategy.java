/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;

/**
 * This strategy handles the {@link org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute} command.
 */
@Immutable
final class RetrieveAttributeStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<RetrieveAttribute, JsonValue> {

    /**
     * Constructs a new {@code RetrieveAttributeStrategy} object.
     */
    RetrieveAttributeStrategy() {
        super(RetrieveAttribute.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveAttribute command) {

        return extractAttributes(thing)
                .map(attributes -> getAttributeValueResult(attributes, context.getThingId(), command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.attributesNotFound(context.getThingId(), command.getDittoHeaders())));
    }

    private Optional<Attributes> extractAttributes(final @Nullable Thing thing) {
        return getThingOrThrow(thing).getAttributes();
    }

    private Result getAttributeValueResult(final JsonObject attributes, final String thingId,
            final RetrieveAttribute command, @Nullable final Thing thing) {

        final JsonPointer attributePointer = command.getAttributePointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return attributes.getValue(attributePointer)
                .map(value -> RetrieveAttributeResponse.of(thingId, attributePointer, value, dittoHeaders))
                .map(response -> ResultFactory.newQueryResult(command, thing, response, this))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.attributeNotFound(thingId, attributePointer, dittoHeaders)));
    }


    @Override
    public Optional<JsonValue> determineETagEntity(final RetrieveAttribute command, @Nullable final Thing thing) {
        return extractAttributes(thing)
                .flatMap(attributes -> attributes.getValue(command.getAttributePointer()));
    }
}
