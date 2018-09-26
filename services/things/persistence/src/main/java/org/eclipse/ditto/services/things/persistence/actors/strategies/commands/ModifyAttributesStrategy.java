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
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesModified;

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
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final ModifyAttributes command) {
        final Thing nonNullThing = getThingOrThrow(thing);
        ThingCommandSizeValidator.getInstance().ensureValidSize(() -> {
            final long lengthWithOutAttributes = nonNullThing.removeAttributes()
                    .toJsonString()
                    .length();
            final long attributesLength = command.getAttributes().toJsonString().length()
                    + "attributes".length() + 5L;
            return lengthWithOutAttributes + attributesLength;
        }, command::getDittoHeaders);

        return nonNullThing.getAttributes()
                .map(attributes -> getModifyResult(context, nextRevision, command))
                .orElseGet(() -> getCreateResult(context, nextRevision, command));
    }

    private Result getModifyResult(final Context context, final long nextRevision,
            final ModifyAttributes command) {
        final String thingId = context.getThingId();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newMutationResult(command,
                AttributesModified.of(thingId, command.getAttributes(), nextRevision, getEventTimestamp(),
                        dittoHeaders), ModifyAttributesResponse.modified(thingId, dittoHeaders), this);
    }

    private Result getCreateResult(final Context context, final long nextRevision,
            final ModifyAttributes command) {
        final String thingId = context.getThingId();
        final Attributes attributes = command.getAttributes();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        return ResultFactory.newMutationResult(command,
                AttributesCreated.of(thingId, attributes, nextRevision, getEventTimestamp(), dittoHeaders),
                ModifyAttributesResponse.created(thingId, attributes, dittoHeaders), this);
    }

    @Override
    public Optional<Attributes> determineETagEntity(final ModifyAttributes command, @Nullable final Thing thing) {
        return getThingOrThrow(thing).getAttributes();
    }
}
