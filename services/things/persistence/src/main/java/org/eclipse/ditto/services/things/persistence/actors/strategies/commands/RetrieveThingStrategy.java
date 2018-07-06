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

import static org.eclipse.ditto.services.things.persistence.actors.strategies.commands.ResultFactory.newResult;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.AbstractCommandStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.CommandStrategy;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;

/**
 * This strategy handles the {@link RetrieveThing} command.
 */
@NotThreadSafe
final class RetrieveThingStrategy extends AbstractCommandStrategy<RetrieveThing> {

    /**
     * Constructs a new {@code RetrieveThingStrategy} object.
     */
    RetrieveThingStrategy() {
        super(RetrieveThing.class);
    }

    @Override
    protected boolean isDefined(final Context context, final RetrieveThing command) {
        return Objects.equals(context.getThingId(), command.getId())
                && null != context.getThing()
                && !isThingDeleted(context.getThing());
    }

    @Override
    protected CommandStrategy.Result doApply(final CommandStrategy.Context context, final RetrieveThing command) {
        final String thingId = context.getThingId();
        final Thing thing = context.getThing();
        final Optional<Long> snapshotRevisionOptional = command.getSnapshotRevision();
        if (snapshotRevisionOptional.isPresent()) {

            try {
                final ThingSnapshotter<?, ?> thingSnapshotter = context.getThingSnapshotter();
                final Optional<Thing> thingOptional = thingSnapshotter.loadSnapshot(snapshotRevisionOptional.get())
                        // any better way to do this??
                        .toCompletableFuture().get();
                if (thingOptional.isPresent()) {
                    return newResult(respondWithLoadSnapshotResult(command, thingOptional.get()));
                } else {
                    return newResult(respondWithNotAccessibleException(command));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // TODO better exception
                return newResult(respondWithNotAccessibleException(command));
            } catch (ExecutionException e) {
                // TODO better exception
                return newResult(respondWithNotAccessibleException(command));
            }
        } else {
            final JsonObject thingJson = command.getSelectedFields()
                    .map(sf -> thing.toJson(command.getImplementedSchemaVersion(), sf))
                    .orElseGet(() -> thing.toJson(command.getImplementedSchemaVersion()));

            return newResult(RetrieveThingResponse.of(thingId, thingJson, command.getDittoHeaders()));
        }
    }

    private WithDittoHeaders<RetrieveThingResponse> respondWithLoadSnapshotResult(final RetrieveThing command,
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
    protected Result unhandled(final Context context, final RetrieveThing command) {
        return newResult(new ThingNotAccessibleException(context.getThingId(), command.getDittoHeaders()));
    }
}