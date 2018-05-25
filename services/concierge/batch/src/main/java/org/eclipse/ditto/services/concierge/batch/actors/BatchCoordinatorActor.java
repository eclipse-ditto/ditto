/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.batch.actors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.batch.ExecuteBatch;
import org.eclipse.ditto.signals.commands.batch.ExecuteBatchResponse;
import org.eclipse.ditto.signals.commands.batch.exceptions.BatchAlreadyExecutingException;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.batch.BatchCommandExecuted;
import org.eclipse.ditto.signals.events.batch.BatchExecutionFinished;
import org.eclipse.ditto.signals.events.batch.BatchExecutionStarted;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import scala.concurrent.duration.Duration;

/**
 * Actor which handles batch execution of commands.
 */
final class BatchCoordinatorActor extends AbstractPersistentActor {

    /**
     * The name prefix of this Actor.
     */
    static final String ACTOR_NAME_PREFIX = "batch-coordinator-";

    private static final String PERSISTENCE_ID_PREFIX = "batch:";

    private static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-batch-journal";
    private static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-batch-snapshots";

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 60;

    private static final String BATCH_ID_FIELD = "batchId";
    private static final String RANDOM_FIELD = "random";
    private static final String ORIGINAL_CORRELATION_ID = "originalCorrelationId";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final ActorRef conciergeForwarder;
    private final Set<String> pendingCommands;
    private final Map<String, Command> commands;
    private final List<CommandResponse> commandResponses;

    private String batchId;
    private ActorRef originalSender;
    private Cancellable shutdown;

    private BatchCoordinatorActor(final String batchId, final ActorRef pubSubMediator, final ActorRef conciergeForwarder) {
        this.batchId = batchId;
        this.conciergeForwarder = conciergeForwarder;
        this.pubSubMediator = pubSubMediator;

        pendingCommands = new HashSet<>();
        commands = new HashMap<>();
        commandResponses = new ArrayList<>();
    }

    /**
     * Creates Akka configuration object Props for this BatchCoordinatorActor.
     *
     * @param batchId the identifier of the batch which this actor handles.
     * @param conciergeForwarder the ref of the conciergeForwarder.
     * @param pubSubMediator the mediator to use for distributed pubsub.
     * @return the Akka configuration Props object.
     */
    static Props props(final String batchId, final ActorRef pubSubMediator, final ActorRef conciergeForwarder) {
        return Props.create(BatchCoordinatorActor.class, new Creator<BatchCoordinatorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public BatchCoordinatorActor create() {
                return new BatchCoordinatorActor(batchId, pubSubMediator, conciergeForwarder);
            }
        });
    }

    @Override
    public String persistenceId() {
        return PERSISTENCE_ID_PREFIX + batchId;
    }

    @Override
    public String journalPluginId() {
        return JOURNAL_PLUGIN_ID;
    }

    @Override
    public String snapshotPluginId() {
        return SNAPSHOT_PLUGIN_ID;
    }

    @Override
    public Receive createReceiveRecover() {
        return ReceiveBuilder.create()
                .match(BatchExecutionStarted.class, event -> {
                    batchId = event.getBatchId();
                    event.getCommands()
                            .forEach(command -> {
                                final String correlationId = command.getDittoHeaders().getCorrelationId().get();
                                commands.put(correlationId, command);
                                pendingCommands.add(correlationId);
                            });
                })
                .match(BatchCommandExecuted.class, event -> {
                    final CommandResponse response = event.getResponse();
                    final String correlationId = response.getDittoHeaders()
                            .getCorrelationId()
                            .orElseThrow(() -> new IllegalStateException("Received a CommandResponse without " +
                                    "Correlation ID!"));
                    pendingCommands.remove(correlationId);
                    commandResponses.add(response);
                })
                .match(BatchExecutionFinished.class,
                        event -> log.debug("Recovered finished batch '{}'.", batchId))
                .match(RecoveryCompleted.class, rc -> {
                    log.debug("Recovery completed");
                    if (!pendingCommands.isEmpty()) {
                        log.debug("Resuming execution of batch '{}'.", batchId);
                        pendingCommands.forEach(
                                correlationId -> conciergeForwarder.tell(commands.get(correlationId), getSelf()));
                        becomeCommandResponseAwaiting();
                    } else {
                        log.debug("No pending commands - shutting down in {} seconds.",
                                SHUTDOWN_TIMEOUT_SECONDS);
                        scheduleShutdown();
                    }
                })
                .matchAny(m -> log.warning("Unknown recover message: {}", m))
                .build();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ExecuteBatch.class, this::executeDryRun)
                .matchAny(m -> log.warning("Got unknown message, expected an 'ExecuteBatch' command: {}", m))
                .build();
    }

    private void executeDryRun(final ExecuteBatch command) {
        cancelShutdown();

        originalSender = getSender();

        command.getCommands().forEach(this::tellCommandAsDryRun);

        becomeDryRunCommandResponseAwaiting();
    }

    private void tellCommandAsDryRun(final Command command) {
        final String correlationId = encodeCorrelationId(command.getDittoHeaders());
        final DittoHeadersBuilder dittoHeadersBuilder = command.getDittoHeaders()
                .toBuilder()
                .correlationId(correlationId);

        commands.put(correlationId, command.setDittoHeaders(dittoHeadersBuilder.build()));
        pendingCommands.add(correlationId);

        final Command commandAsDryRun = command.setDittoHeaders(dittoHeadersBuilder.dryRun(true).build());
        conciergeForwarder.tell(commandAsDryRun, getSelf());
    }

    private void becomeDryRunCommandResponseAwaiting() {
        getContext().become(ReceiveBuilder.create()
                .match(ExecuteBatch.class, this::batchAlreadyExecuting)
                .match(CommandResponse.class, response -> {
                    pendingCommands.remove(response.getDittoHeaders()
                            .getCorrelationId()
                            .orElseThrow(() -> new IllegalStateException("Received a CommandResponse without " +
                                    "Correlation ID!")));

                    if (pendingCommands.isEmpty()) {
                        final BatchExecutionStarted batchExecutionStarted =
                                BatchExecutionStarted.of(batchId, Instant.now(),
                                        new ArrayList<>(commands.values()),
                                        buildDittoHeaders());

                        persist(batchExecutionStarted, event -> {
                            log.info("Batch with ID '{}' started.", batchId);

                            originalSender.tell(ExecuteBatchResponse.of(batchId, buildDittoHeaders()), getSelf());
                            commands.forEach((correlationId, command) -> {
                                conciergeForwarder.tell(command, getSelf());
                                pendingCommands.add(correlationId);
                            });

                            notifySubscribers(batchExecutionStarted);
                            becomeCommandResponseAwaiting();
                        });
                    }
                })
                .match(DittoRuntimeException.class, exception -> {
                    final String correlationId = exception.getDittoHeaders()
                            .getCorrelationId()
                            .orElseThrow(() -> new IllegalStateException("Received a DittoRuntimeException without " +
                                    "Correlation ID!"));

                    final ThingErrorResponse errorResponse =
                            ThingErrorResponse.of(exception,
                                    buildDittoHeaders());

                    pendingCommands.remove(correlationId);
                    originalSender.tell(errorResponse, getSelf());
                    scheduleShutdown();
                    becomeShutdownAwaiting();
                })
                .matchAny(m -> log.warning("Got unknown message: {}", m))
                .build());
    }

    private void becomeCommandResponseAwaiting() {
        getContext().become(ReceiveBuilder.create()
                .match(ExecuteBatch.class, this::batchAlreadyExecuting)
                .match(CommandResponse.class, this::handleCommandResponse)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .matchAny(m -> log.warning("Got unknown message: {}", m))
                .build());
    }

    private void batchAlreadyExecuting(final ExecuteBatch command) {
        final ThingErrorResponse response = ThingErrorResponse.of(BatchAlreadyExecutingException.newBuilder(batchId)
                .dittoHeaders(command.getDittoHeaders())
                .build());
        originalSender.tell(response, getSelf());
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception) {
        handleCommandResponse(ThingErrorResponse.of(exception));
    }

    private void handleCommandResponse(final CommandResponse response) {
        final String correlationId = response.getDittoHeaders()
                .getCorrelationId()
                .orElseThrow(() -> new IllegalStateException("Received a CommandResponse without " +
                        "Correlation ID!"));
        final BatchCommandExecuted commandExecuted =
                BatchCommandExecuted.of(response.getDittoHeaders().getCorrelationId()
                        .orElseThrow(() -> new IllegalStateException("encountered CommandResponse without correlationId")),
                response, Instant.now());
        persist(commandExecuted, event -> {
            log.info("Received '{}' for Batch with ID '{}'.", response.getName(), batchId);

            pendingCommands.remove(correlationId);
            commandResponses.add(unfixCorrelationId(response));

            if (pendingCommands.isEmpty()) {
                final BatchExecutionFinished batchExecutionFinished = BatchExecutionFinished.of(batchId,
                        Instant.now(), commandResponses,
                        buildDittoHeaders());
                persist(batchExecutionFinished, batchExecutionFinishedPersisted -> {
                    log.info("Batch with ID '{}' finished.", batchId);
                    notifySubscribers(batchExecutionFinishedPersisted);
                    scheduleShutdown();
                    becomeShutdownAwaiting();
                });
            }
        });
    }

    private void notifySubscribers(final Event event) {
        pubSubMediator.tell(new DistributedPubSubMediator.Publish(event.getType(),
                event, true), getSelf());
    }

    private DittoHeaders buildDittoHeaders() {
        return DittoHeaders.newBuilder()
                .correlationId(batchId)
                .build();
    }

    private String encodeCorrelationId(final DittoHeaders dittoHeaders) {
        return JsonObject.newBuilder()
                .set(BATCH_ID_FIELD, batchId)
                .set(RANDOM_FIELD, UUID.randomUUID().toString())
                .set(ORIGINAL_CORRELATION_ID, dittoHeaders.getCorrelationId().orElse(UUID.randomUUID().toString()))
                .build()
                .toString();
    }

    private JsonObject decodeCommandCorrelationId(final String commandCorrelationId) {
        return JsonFactory.newObject(commandCorrelationId);
    }

    private CommandResponse unfixCorrelationId(final CommandResponse response) {
        final String correlationId = response.getDittoHeaders()
                .getCorrelationId()
                .flatMap(s -> decodeCommandCorrelationId(s).getValue(ORIGINAL_CORRELATION_ID))
                .map(JsonValue::asString)
                .orElse(null);
        final DittoHeaders dittoHeaders = response.getDittoHeaders().toBuilder()
                .correlationId(correlationId)
                .build();
        return response.setDittoHeaders(dittoHeaders);
    }

    private void scheduleShutdown() {
        shutdown =
                getContext().system()
                        .scheduler()
                        .scheduleOnce(Duration.create(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                                () -> {
                                    if (null != getContext()) {
                                        getContext().stop(getSelf());
                                    }
                                },
                                getContext().system().dispatcher());
    }

    private void becomeShutdownAwaiting() {
        getContext().become(ReceiveBuilder.create()
                .matchAny(m -> log.debug("Got message while waiting for shutdown: {}", m))
                .build());
    }

    private void cancelShutdown() {
        if (null != shutdown) {
            shutdown.cancel();
            getContext().unbecome();
        }
    }

}
