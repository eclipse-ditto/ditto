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
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;

/**
 * This strategy handles the {@link RetrieveThing} command.
 */
@NotThreadSafe
final class RetrieveThingStrategy extends AbstractThingCommandStrategy<RetrieveThing> {

    /**
     * Constructs a new {@code RetrieveThingStrategy} object.
     */
    public RetrieveThingStrategy() {
        super(RetrieveThing.class);
    }

    // TODO pass in constructor??
    ThingSnapshotter<?, ?> thingSnapshotter;

    @Override
    public BiFunction<Context, RetrieveThing, Boolean> getPredicate() {

        return (ctx, command) ->
                Objects.equals(ctx.getThingId(), command.getId())
                        && null != ctx.getThing()
                        && !isThingDeleted(ctx.getThing());
    }

    @Override
    protected Result doApply(final Context context, final RetrieveThing command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final Optional<Long> snapshotRevisionOptional = command.getSnapshotRevision();
        if (snapshotRevisionOptional.isPresent()) {

            try {
                final Optional<Thing> thingOptional = thingSnapshotter.loadSnapshot(snapshotRevisionOptional.get())
                        // any better way to do this??
                        .toCompletableFuture().get();
                if (thingOptional.isPresent()) {
                    return result(respondWithLoadSnapshotResult(command, thingOptional.get()));
                } else {
                    return result(respondWithNotAccessibleException(command));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // TODO better exception
                return result(respondWithNotAccessibleException(command));
            } catch (ExecutionException e) {
                // TODO better exception
                return result(respondWithNotAccessibleException(command));
            }
        } else {
            final JsonObject thingJson = command.getSelectedFields()
                    .map(sf -> thing.toJson(command.getImplementedSchemaVersion(), sf))
                    .orElseGet(() -> thing.toJson(command.getImplementedSchemaVersion()));

            return result(RetrieveThingResponse.of(thingId, thingJson, command.getDittoHeaders()));
        }
    }

    private CommandResponse<RetrieveThingResponse> respondWithLoadSnapshotResult(final RetrieveThing command,
            final Thing snapshotThing) {

        final JsonObject thingJson = command.getSelectedFields()
                .map(sf -> snapshotThing.toJson(command.getImplementedSchemaVersion(), sf))
                .orElseGet(() -> snapshotThing.toJson(command.getImplementedSchemaVersion()));

        return RetrieveThingResponse.of(command.getThingId(), thingJson, command.getDittoHeaders());
    }

    private DittoRuntimeException respondWithNotAccessibleException(final RetrieveThing command) {
        // reset command headers so that correlationId etc. are preserved
        return new ThingNotAccessibleException(command.getThingId(), command.getDittoHeaders());
    }

    @Override
    public BiFunction<Context, RetrieveThing, Result> getUnhandledFunction() {
        return (context, command) -> result(
                new ThingNotAccessibleException(context.getThingId(), command.getDittoHeaders()));
    }

}