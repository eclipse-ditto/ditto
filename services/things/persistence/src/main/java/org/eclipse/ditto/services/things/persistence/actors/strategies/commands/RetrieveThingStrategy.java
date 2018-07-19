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

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

/**
 * This strategy handles the {@link RetrieveThing} command.
 */
@Immutable
final class RetrieveThingStrategy extends AbstractCommandStrategy<RetrieveThing> {

    private static final short RETRIEVE_THING_TIMEOUT_SECONDS = 120;
    private static final Duration RETRIEVE_THING_TIMEOUT = Duration.ofSeconds(RETRIEVE_THING_TIMEOUT_SECONDS);

    /**
     * Constructs a new {@code RetrieveThingStrategy} object.
     */
    RetrieveThingStrategy() {
        super(RetrieveThing.class);
    }

    @Override
    public boolean isDefined(final Context context, final RetrieveThing command) {
        final boolean thingExists = context.getThing()
                .map(thing -> !isThingDeleted(thing))
                .orElse(false);

        return Objects.equals(context.getThingId(), command.getId()) && thingExists;
    }

    @Override
    protected Result doApply(final Context context, final RetrieveThing command) {
        final Thing thing = context.getThingOrThrow();

        return command.getSnapshotRevision()
                .map(snapshotRevision -> getRetrieveThingFromSnapshotterResult(snapshotRevision, context, command))
                .orElseGet(() -> getRetrieveThingResult(thing, command));
    }

    private static Result getRetrieveThingFromSnapshotterResult(final long snapshotRevision, final Context context,
            final RetrieveThing command) {

        return tryToLoadThingSnapshot(snapshotRevision, context, command);
    }

    private static Result tryToLoadThingSnapshot(final long snapshotRevision, final Context context,
            final RetrieveThing command) {

        try {
            return loadThingSnapshot(context.getThingSnapshotter(), snapshotRevision, command);
        } catch (final ExecutionException | TimeoutException e) {
            context.getLog().info("Failed to retrieve thing with ID <{}>: {}", context.getThingId(), e.getMessage());
            return ResultFactory.newResult(getThingUnavailableException(command));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            context.getLog().info("Retrieving thing with ID <{}> was interrupted.", context.getThingId());
            return ResultFactory.newResult(getThingUnavailableException(command));
        }
    }

    private static Result loadThingSnapshot(final ThingSnapshotter<?, ?> snapshotter, final long snapshotRevision,
            final RetrieveThing command) throws ExecutionException, InterruptedException, TimeoutException {

        final CompletionStage<Optional<Thing>> completionStage = snapshotter.loadSnapshot(snapshotRevision);
        final CompletableFuture<Optional<Thing>> completableFuture = completionStage.toCompletableFuture();

        return completableFuture.get(RETRIEVE_THING_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
                .map(thing -> getRetrieveThingResult(thing, command))
                .orElseGet(() -> ResultFactory.newResult(
                        new ThingNotAccessibleException(command.getThingId(), command.getDittoHeaders())));
    }

    private static Result getRetrieveThingResult(final Thing thing, final ThingQueryCommand<RetrieveThing> command) {
        return ResultFactory.newResult(RetrieveThingResponse.of(command.getThingId(), getThingJson(thing, command),
                command.getDittoHeaders()));
    }

    private static JsonObject getThingJson(final Thing thing, final ThingQueryCommand<RetrieveThing> command) {
        return command.getSelectedFields()
                .map(selectedFields -> thing.toJson(command.getImplementedSchemaVersion(), selectedFields))
                .orElseGet(() -> thing.toJson(command.getImplementedSchemaVersion()));
    }

    private static DittoRuntimeException getThingUnavailableException(final RetrieveThing command) {
        // reset command headers so that correlation-id etc. is preserved
        return ThingUnavailableException.newBuilder(command.getThingId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    @Override
    protected Result unhandled(final Context context, final RetrieveThing command) {
        return ResultFactory.newResult(
                new ThingNotAccessibleException(context.getThingId(), command.getDittoHeaders()));
    }

}