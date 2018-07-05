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
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFieldSelector;
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
@NotThreadSafe
final class SudoRetrieveThingStrategy extends AbstractThingCommandStrategy<SudoRetrieveThing> {

    /**
     * Constructs a new {@code SudoRetrieveThingStrategy} object.
     */
    public SudoRetrieveThingStrategy() {
        super(SudoRetrieveThing.class);
    }

    @Override
    public BiFunction<Context, SudoRetrieveThing, Boolean> getPredicate() {
        return (ctx, command) ->
                Objects.equals(ctx.getThingId(), command.getId())
                        && null != ctx.getThing()
                        && !isThingDeleted(ctx.getThing());
    }

    @Override
    protected Result doApply(final Context context, final SudoRetrieveThing command) {
        final Thing thing = context.getThing();
        final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
        final JsonSchemaVersion versionToUse = determineSchemaVersion(command, thing);
        final JsonObject thingJson = selectedFields
                .map(sf -> thing.toJson(versionToUse, sf, FieldType.regularOrSpecial()))
                .orElseGet(() -> thing.toJson(versionToUse, FieldType.regularOrSpecial()));

        return ImmutableResult.of(SudoRetrieveThingResponse.of(thingJson, command.getDittoHeaders()));
    }

    private JsonSchemaVersion determineSchemaVersion(final SudoRetrieveThing command, final Thing thing) {
        return command.useOriginalSchemaVersion()
                ? getOriginalSchemaVersion(thing)
                : command.getImplementedSchemaVersion();
    }

    private JsonSchemaVersion getOriginalSchemaVersion(final Thing thing) {
        return null != thing ? thing.getImplementedSchemaVersion() : JsonSchemaVersion.LATEST;
    }

    @Override
    public BiFunction<Context, SudoRetrieveThing, Result> getUnhandledFunction() {
        return (ctx, command) -> ImmutableResult.of(
                new ThingNotAccessibleException(ctx.getThingId(), command.getDittoHeaders()));
    }
}