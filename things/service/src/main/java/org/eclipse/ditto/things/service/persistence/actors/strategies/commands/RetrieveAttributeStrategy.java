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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttribute;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributeResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttribute} command.
 */
@Immutable
final class RetrieveAttributeStrategy extends AbstractThingCommandStrategy<RetrieveAttribute> {

    /**
     * Constructs a new {@code RetrieveAttributeStrategy} object.
     */
    RetrieveAttributeStrategy() {
        super(RetrieveAttribute.class);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final RetrieveAttribute command,
            @Nullable final Metadata metadata) {

        return extractAttributes(thing)
                .map(attributes -> getAttributeValueResult(attributes, context.getState(), command, thing))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.attributesNotFound(context.getState(), command.getDittoHeaders()), command));
    }

    private Optional<Attributes> extractAttributes(final @Nullable Thing thing) {
        return getEntityOrThrow(thing).getAttributes();
    }

    private Result<ThingEvent<?>> getAttributeValueResult(final JsonObject attributes, final ThingId thingId,
            final RetrieveAttribute command, @Nullable final Thing thing) {

        final JsonPointer attributePointer = command.getAttributePointer();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return attributes.getValue(attributePointer)
                .map(value -> RetrieveAttributeResponse.of(thingId, attributePointer, value, dittoHeaders))
                .<Result<ThingEvent<?>>>map(response ->
                        ResultFactory.newQueryResult(command, appendETagHeaderIfProvided(command, response, thing)))
                .orElseGet(() -> ResultFactory.newErrorResult(
                        ExceptionFactory.attributeNotFound(thingId, attributePointer, dittoHeaders), command));
    }


    @Override
    public Optional<EntityTag> previousEntityTag(final RetrieveAttribute command,
            @Nullable final Thing previousEntity) {
        return nextEntityTag(command, previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final RetrieveAttribute command, @Nullable final Thing newEntity) {
        return extractAttributes(newEntity)
                .flatMap(attributes -> attributes.getValue(command.getAttributePointer()))
                .flatMap(EntityTag::fromEntity);
    }
}
