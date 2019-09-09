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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

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
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final DeleteAttributes command) {

        return extractAttributes(thing)
                .map(attributes -> getDeleteAttributesResult(context, nextRevision, command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.attributesNotFound(context.getEntityId(), command.getDittoHeaders())));
    }

    private Optional<Attributes> extractAttributes(final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getAttributes();
    }

    private Result<ThingEvent> getDeleteAttributesResult(final Context<ThingId> context, final long nextRevision,
            final DeleteAttributes command, @Nullable Thing thing) {
        final ThingId thingId = context.getEntityId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                DeleteAttributesResponse.of(thingId, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command,
                AttributesDeleted.of(thingId, nextRevision, getEventTimestamp(), dittoHeaders), response);
    }

    @Override
    public Optional<Attributes> determineETagEntity(final DeleteAttributes command, @Nullable final Thing thing) {
        return Optional.empty();
    }
}
