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

import java.util.Objects;

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
final class SudoRetrieveThingStrategy extends AbstractCommandStrategy<SudoRetrieveThing> {

    /**
     * Constructs a new {@code SudoRetrieveThingStrategy} object.
     */
    SudoRetrieveThingStrategy() {
        super(SudoRetrieveThing.class);
    }

    @Override
    public boolean isDefined(final Context context, final SudoRetrieveThing command) {
        final boolean thingExists = context.getThing()
                .map(thing -> !isThingDeleted(thing))
                .orElse(false);

        return Objects.equals(context.getThingId(), command.getId()) && thingExists;
    }

    @Override
    protected Result doApply(final Context context, final SudoRetrieveThing command) {
        final Thing thing = context.getThingOrThrow();

        final JsonSchemaVersion jsonSchemaVersion = determineSchemaVersion(command, thing);
        final JsonObject thingJson = command.getSelectedFields()
                .map(selectedFields -> thing.toJson(jsonSchemaVersion, selectedFields, FieldType.regularOrSpecial()))
                .orElseGet(() -> thing.toJson(jsonSchemaVersion, FieldType.regularOrSpecial()));

        return ResultFactory.newResult(SudoRetrieveThingResponse.of(thingJson, command.getDittoHeaders()));
    }

    private static JsonSchemaVersion determineSchemaVersion(final SudoRetrieveThing command, final Thing thing) {
        return command.useOriginalSchemaVersion()
                ? thing.getImplementedSchemaVersion()
                : command.getImplementedSchemaVersion();
    }

    @Override
    protected Result unhandled(final Context context, final SudoRetrieveThing command) {
        return ResultFactory.newResult(
                new ThingNotAccessibleException(context.getThingId(), command.getDittoHeaders()));
    }

}