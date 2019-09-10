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
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesModified;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link ModifyAttributes} command.
 */
@Immutable
public final class ModifyAttributesStrategy extends
        AbstractConditionalHeadersCheckingCommandStrategy<ModifyAttributes, Attributes> {

    /**
     * Constructs a new {@code ModifyAttributesStrategy} object.
     */
    ModifyAttributesStrategy() {
        super(ModifyAttributes.class);
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final ModifyAttributes command) {
        final Thing nonNullThing = getEntityOrThrow(thing);
        ThingCommandSizeValidator.getInstance().ensureValidSize(() -> {
            final long lengthWithOutAttributes = nonNullThing.removeAttributes()
                    .toJsonString()
                    .length();
            final long attributesLength = command.getAttributes().toJsonString().length()
                    + "attributes".length() + 5L;
            return lengthWithOutAttributes + attributesLength;
        }, command::getDittoHeaders);

        return nonNullThing.getAttributes()
                .map(attributes -> getModifyResult(context, nextRevision, command, thing))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing));
    }

    private Result<ThingEvent> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyAttributes command, @Nullable final Thing thing) {
        final ThingId thingId = context.getState();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent event =
                AttributesModified.of(thingId, command.getAttributes(), nextRevision, getEventTimestamp(),
                        dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyAttributesResponse.modified(thingId, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyAttributes command, @Nullable final Thing thing) {
        final ThingId thingId = context.getState();
        final Attributes attributes = command.getAttributes();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent event =
                AttributesCreated.of(thingId, attributes, nextRevision, getEventTimestamp(), dittoHeaders);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyAttributesResponse.created(thingId, attributes, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<Attributes> determineETagEntity(final ModifyAttributes command, @Nullable final Thing thing) {
        return Optional.of(command.getAttributes());
    }
}
