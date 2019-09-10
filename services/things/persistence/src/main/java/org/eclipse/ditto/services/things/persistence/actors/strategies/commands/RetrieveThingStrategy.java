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
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This strategy handles the {@link RetrieveThing} command.
 */
@Immutable
final class RetrieveThingStrategy extends AbstractConditionalHeadersCheckingCommandStrategy<RetrieveThing, Thing> {

    /**
     * Constructs a new {@code RetrieveThingStrategy} object.
     */
    RetrieveThingStrategy() {
        super(RetrieveThing.class);
    }

    @Override
    public boolean isDefined(final Context<ThingId> context, @Nullable final Thing thing,
            final RetrieveThing command) {
        final boolean thingExists = Optional.ofNullable(thing)
                .map(t -> !t.isDeleted())
                .orElse(false);

        return Objects.equals(context.getState(), command.getEntityId()) && thingExists;
    }

    @Override
    protected Result<ThingEvent> doApply(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveThing command) {

        return ResultFactory.newQueryResult(command,
                appendETagHeaderIfProvided(command, getRetrieveThingResponse(thing, command), thing));
    }

    private static WithDittoHeaders getRetrieveThingResponse(@Nullable final Thing thing,
            final ThingQueryCommand<RetrieveThing> command) {
        if (thing != null) {
            return RetrieveThingResponse.of(command.getThingEntityId(), getThingJson(thing, command),
                    command.getDittoHeaders());
        } else {
            return notAccessible(command);
        }
    }

    private static JsonObject getThingJson(final Thing thing, final ThingQueryCommand<RetrieveThing> command) {
        return command.getSelectedFields()
                .map(selectedFields -> thing.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> thing.toJson(command.getImplementedSchemaVersion()));
    }

    private static ThingNotAccessibleException notAccessible(final ThingQueryCommand<?> command) {
        return new ThingNotAccessibleException(command.getThingEntityId(), command.getDittoHeaders());
    }

    @Override
    public Result<ThingEvent> unhandled(final Context<ThingId> context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveThing command) {
        return ResultFactory.newErrorResult(
                new ThingNotAccessibleException(context.getState(), command.getDittoHeaders()));
    }

    @Override
    public Optional<Thing> determineETagEntity(final RetrieveThing command, @Nullable final Thing thing) {
        return Optional.ofNullable(thing);
    }
}
