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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;

/**
 * This strategy handles the {@link SudoRetrieveThing} command.
 */
@Immutable
final class SudoRetrieveThingStrategy
        extends AbstractConditionalHeadersCheckingCommandStrategy<SudoRetrieveThing, Thing> {

    /**
     * Constructs a new {@code SudoRetrieveThingStrategy} object.
     */
    SudoRetrieveThingStrategy() {
        super(SudoRetrieveThing.class);
    }

    @Override
    public boolean isDefined(final Context context, @Nullable final Thing thing,
            final SudoRetrieveThing command) {
        final boolean thingExists = Optional.ofNullable(thing)
                .map(t -> !t.isDeleted())
                .orElse(false);

        return Objects.equals(context.getThingId(), command.getId()) && thingExists;
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final SudoRetrieveThing command) {

        final Thing theThing = getThingOrThrow(thing);

        final JsonSchemaVersion jsonSchemaVersion = determineSchemaVersion(command, theThing);
        final JsonObject thingJson = command.getSelectedFields()
                .map(selectedFields -> theThing.toJson(jsonSchemaVersion, selectedFields, FieldType.regularOrSpecial()))
                .orElseGet(() -> theThing.toJson(jsonSchemaVersion, FieldType.regularOrSpecial()));

        return ResultFactory.newQueryResult(command, thing,
                SudoRetrieveThingResponse.of(thingJson, command.getDittoHeaders()), this);
    }

    private static JsonSchemaVersion determineSchemaVersion(final SudoRetrieveThing command, final Thing thing) {
        return command.useOriginalSchemaVersion()
                ? thing.getImplementedSchemaVersion()
                : command.getImplementedSchemaVersion();
    }

    @Override
    protected Result unhandled(final Context context, @Nullable final Thing thing,
            final long nextRevision, final SudoRetrieveThing command) {
        return ResultFactory.newErrorResult(
                new ThingNotAccessibleException(context.getThingId(), command.getDittoHeaders()));
    }

    @Override
    public Optional<Thing> determineETagEntity(final SudoRetrieveThing command, @Nullable final Thing thing) {
        return Optional.ofNullable(thing);
    }
}
