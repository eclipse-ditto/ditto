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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;

/**
 * This strategy handles the {@link DeleteAttributes} command.
 */
@Immutable
final class DeleteAttributesStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<DeleteAttributes, Attributes> {

    /**
     * Constructs a new {@code DeleteAttributesStrategy} object.
     */
    DeleteAttributesStrategy() {
        super(DeleteAttributes.class);
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final DeleteAttributes command) {

        return extractAttributes(thing)
                .map(attributes -> getDeleteAttributesResult(context, nextRevision, command))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.attributesNotFound(context.getThingId(), command.getDittoHeaders())));
    }

    private Optional<Attributes> extractAttributes(final @Nullable Thing thing) {
        return getThingOrThrow(thing).getAttributes();
    }

    private Result getDeleteAttributesResult(final Context context, final long nextRevision,
            final DeleteAttributes command) {
        final String thingId = context.getThingId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newMutationResult(command,
                AttributesDeleted.of(thingId, nextRevision, getEventTimestamp(), dittoHeaders),
                DeleteAttributesResponse.of(thingId, dittoHeaders), this);
    }

    @Override
    public Optional<Attributes> determineETagEntity(final DeleteAttributes command,
            @Nullable final Thing thing) {
        return extractAttributes(thing);
    }
}
