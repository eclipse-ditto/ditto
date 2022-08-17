/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.dispatching;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * This class allows chaining futures related for a single entity.
 * This means that you can be sure that previous tasks for the entity are completed when you're reiving the TaskResult
 * as response.
 */
final class EntityTaskResultSequentializer extends AbstractActor {

    static final String ACTOR_NAME = "entity-task-scheduler";

    /**
     * Remembers running tasks for a certain entity ID. May contain an already completed future.
     */
    private final Map<EntityId, CompletionStage<?>> taskCsPerEntityId;
    private final DittoDiagnosticLoggingAdapter log;
    private final Counter scheduledTasks;
    private final Counter completedTasks;

    private EntityTaskResultSequentializer() {
        taskCsPerEntityId = new HashMap<>();
        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        scheduledTasks = DittoMetrics.counter("scheduled_tasks");
        completedTasks = DittoMetrics.counter("completed_tasks");
    }

    static Props props() {
        return Props.create(EntityTaskResultSequentializer.class);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Task.class, this::scheduleTask)
                .match(TaskComplete.class, this::taskComplete)
                .matchAny(message -> log.warning("UnknownMessage <{}>", message))
                .build();
    }

    private void scheduleTask(final Task<?> task) {
        final ActorRef sender = sender();
        final CompletionStage<?> taskCs = taskCsPerEntityId.compute(task.entityId(), (entityId, previousTaskCS) -> {
            final CompletionStage<?> previous =
                    previousTaskCS != null ? previousTaskCS : CompletableFuture.completedStage(null);
            return scheduleTaskAfter(previous, task);
        });
        scheduledTasks.increment();

        taskCs.whenComplete((result, error) -> {
            final TaskResult<?> taskResult = new TaskResult<>(result, error);
            sender.tell(taskResult, ActorRef.noSender());
        });
    }

    private void taskComplete(final TaskComplete taskComplete) {
        taskCsPerEntityId.compute(taskComplete.entityId(), (entityId, previousTaskCs) -> {
            if (previousTaskCs == null) {
                log.warning("PreviousTaskCs must never be null on task completion. We at least expect the cs for " +
                        "the task which is completed by this message.");
                return null;
            } else if (previousTaskCs.toCompletableFuture().isDone()) {
                return null;
            } else {
                return previousTaskCs;
            }
        });
        completedTasks.increment();
    }

    /**
     * Schedule an enforcement task based on previous completion stage of a task for an entity.
     * Informs self about completion by sending TaskComplete to self when the scheduled task has been completed.
     *
     * @param previousTaskCompletion in-flight tasks for the same entity.
     * @param task the task to schedule.
     * @return the next in-flight task, including the scheduled task.
     * Completes after all previous tasks are completed as well.
     */
    private CompletionStage<?> scheduleTaskAfter(final CompletionStage<?> previousTaskCompletion, final Task<?> task) {
        return previousTaskCompletion
                .exceptionally(error -> null) //future tasks should ignore failures of previous tasks
                .thenCompose(lastResult -> task.startedStage()
                        .whenComplete((result, error) ->
                                self().tell(new TaskComplete(task.entityId()), ActorRef.noSender())));
    }

    record Task<R>(EntityId entityId, CompletionStage<R> startedStage) {}

    record TaskResult<R>(@Nullable R result, @Nullable Throwable error) {}

    private record TaskComplete(EntityId entityId) {}

}
