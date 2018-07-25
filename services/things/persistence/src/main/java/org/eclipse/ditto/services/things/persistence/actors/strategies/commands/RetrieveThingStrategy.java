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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

import akka.event.DiagnosticLoggingAdapter;

/**
 * This strategy handles the {@link RetrieveThing} command.
 */
@Immutable
final class RetrieveThingStrategy extends AbstractCommandStrategy<RetrieveThing> {

    /**
     * Constructs a new {@code RetrieveThingStrategy} object.
     */
    RetrieveThingStrategy() {
        super(RetrieveThing.class);
    }

    @Override
    public boolean isDefined(final Context context, @Nullable final Thing thing,
            final RetrieveThing command) {
        final boolean thingExists = Optional.ofNullable(thing)
                .map(t -> !isThingDeleted(t))
                .orElse(false);

        return Objects.equals(context.getThingId(), command.getId()) && thingExists;
    }

    @Override
    protected Result doApply(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveThing command) {

        return command.getSnapshotRevision()
                .map(snapshotRevision -> getRetrieveThingFromSnapshotterResult(snapshotRevision, context, command))
                .orElseGet(() -> ResultFactory.newResult(getRetrieveThingResponse(thing, command)));
    }

    private static Result getRetrieveThingFromSnapshotterResult(final long snapshotRevision, final Context context,
            final RetrieveThing command) {

        return tryToLoadThingSnapshot(snapshotRevision, context, command);
    }

    private static Result tryToLoadThingSnapshot(final long snapshotRevision, final Context context,
            final RetrieveThing command) {

        return ResultFactory.newResult(
                loadThingSnapshot(context.getThingSnapshotter(), snapshotRevision, command).handle((message, error) -> {
                    if (error != null) {
                        final DiagnosticLoggingAdapter log = context.getLog();
                        LogUtil.enhanceLogWithCorrelationId(log, command);
                        log.error(error, "Failed to retrieve thing with ID <{}>", context.getThingId());
                    }
                    return message != null
                            ? message
                            : getThingUnavailableException(command);
                }));
    }

    private static CompletionStage<WithDittoHeaders> loadThingSnapshot(
            final ThingSnapshotter<?, ?> snapshotter,
            final long snapshotRevision,
            final RetrieveThing command) {

        final CompletionStage<Optional<Thing>> completionStage = snapshotter.loadSnapshot(snapshotRevision);
        final CompletableFuture<Optional<Thing>> completableFuture = completionStage.toCompletableFuture();

        return completableFuture.thenApply(thingOpt ->
                thingOpt.map(thing -> getRetrieveThingResponse(thing, command))
                        .orElseGet(() -> notAccessible(command)));
    }

    private static WithDittoHeaders getRetrieveThingResponse(@Nullable final Thing thing,
            final ThingQueryCommand<RetrieveThing> command) {
        if (thing != null) {
            return RetrieveThingResponse.of(command.getThingId(), getThingJson(thing, command),
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

    private static DittoRuntimeException getThingUnavailableException(final RetrieveThing command) {
        // reset command headers so that correlation-id etc. is preserved
        return ThingUnavailableException.newBuilder(command.getThingId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static ThingNotAccessibleException notAccessible(final ThingQueryCommand<?> command) {
        return new ThingNotAccessibleException(command.getThingId(), command.getDittoHeaders());
    }

    @Override
    protected Result unhandled(final Context context, @Nullable final Thing thing,
            final long nextRevision, final RetrieveThing command) {
        return ResultFactory.newResult(
                new ThingNotAccessibleException(context.getThingId(), command.getDittoHeaders()));
    }

}
