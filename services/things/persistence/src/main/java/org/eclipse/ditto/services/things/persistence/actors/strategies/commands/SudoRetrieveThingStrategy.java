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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link SudoRetrieveThing} command.
 */
@Immutable
final class SudoRetrieveThingStrategy extends AbstractThingCommandStrategy<SudoRetrieveThing> {

    /**
     * Constructs a new {@code SudoRetrieveThingStrategy} object.
     */
    SudoRetrieveThingStrategy() {
        super(SudoRetrieveThing.class);
    }

    @Override
    public boolean isDefined(final Context<ThingId> context, @Nullable final Thing thing,
            final SudoRetrieveThing command) {
        final boolean thingExists = Optional.ofNullable(thing)
                .map(t -> !t.isDeleted())
                .orElse(false);

        return Objects.equals(context.getState(), command.getEntityId()) && thingExists;
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final SudoRetrieveThing command) {

        final Thing theThing = getEntityOrThrow(thing);

        final JsonSchemaVersion jsonSchemaVersion = determineSchemaVersion(command, theThing);
        final JsonObject thingJson = command.getSelectedFields()
                .map(selectedFields -> theThing.toJson(jsonSchemaVersion, selectedFields, FieldType.regularOrSpecial()))
                .orElseGet(() -> theThing.toJson(jsonSchemaVersion, FieldType.regularOrSpecial()));

        return ResultFactory.newQueryResult(command,
                appendETagHeaderIfProvided(command, SudoRetrieveThingResponse.of(thingJson, command.getDittoHeaders()),
                        thing));
    }

    private static JsonSchemaVersion determineSchemaVersion(final SudoRetrieveThing command, final Thing thing) {
        return command.useOriginalSchemaVersion()
                ? thing.getImplementedSchemaVersion()
                : command.getImplementedSchemaVersion();
    }

    @Override
    public Result<ThingEvent> unhandled(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final SudoRetrieveThing command) {
        return ResultFactory.newErrorResult(
                new ThingNotAccessibleException(context.getState(), command.getDittoHeaders()));
    }

    @Override
    public Optional<?> previousETagEntity(final SudoRetrieveThing command, @Nullable final Thing previousEntity) {
        return nextETagEntity(command, previousEntity);
    }

    @Override
    public Optional<?> nextETagEntity(final SudoRetrieveThing command, @Nullable final Thing newEntity) {
        return Optional.ofNullable(newEntity);
    }
}
