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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

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
 * This means that you can be sure that previous tasks for the entity are completed when you're receiving the TaskResult
 * as response.
 */
final class EntityTaskScheduler extends AbstractActor {

    static final String ACTOR_NAME = "entity-task-scheduler";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    /**
     * Remembers running tasks for a certain entity ID. May contain an already completed future.
     */
    private final Map<EntityId, CompletionStage<?>> taskCsPerEntityId;
    private final Counter scheduledTasks;
    private final Counter completedTasks;

    @SuppressWarnings("unused")
    private EntityTaskScheduler(final String metricsNameTag) {
        taskCsPerEntityId = new HashMap<>();
        scheduledTasks = DittoMetrics.counter("scheduled_tasks")
                .tag("name", metricsNameTag);
        completedTasks = DittoMetrics.counter("completed_tasks")
                .tag("name", metricsNameTag);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param metricsNameTag a name tag to include in the gathered counters/metrics of the actor.
     * @return the Akka configuration Props object.
     */
    static Props props(final String metricsNameTag) {
        return Props.create(EntityTaskScheduler.class, checkNotNull(metricsNameTag, "metricsNameTag"));
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

        if (sender != null && sender != getContext().system().deadLetters()) {
            taskCs.whenComplete((result, error) -> {
                final TaskResult<?> taskResult = new TaskResult<>(result, error);
                sender.tell(taskResult, ActorRef.noSender());
            });
        }
    }

    private void taskComplete(final TaskComplete taskComplete) {

        taskCsPerEntityId.compute(taskComplete.entityId(), (entityId, previousTaskCs) -> {
            if (previousTaskCs == null || previousTaskCs.toCompletableFuture().isDone()) {
                // no pending task was existing or it was already done/deleted
                return null;
            } else {
                return previousTaskCs;
            }
        });
        completedTasks.increment();
    }

    /**
     * Schedule a task based on previous completion stage of a task for an entity.
     * Informs self about completion by sending TaskComplete to self when the scheduled task has been completed.
     *
     * @param previousTaskCompletion in-flight tasks for the same entity.
     * @param task the task to schedule.
     * @return the next in-flight task, including the scheduled task.
     * Completes after all previous tasks are completed as well.
     */
    private CompletionStage<?> scheduleTaskAfter(final CompletionStage<?> previousTaskCompletion, final Task<?> task) {

        return previousTaskCompletion
                .exceptionally(error -> null) // future tasks should ignore failures of previous tasks
                .thenCompose(lastResult -> task.taskRunner().get()
                        .whenComplete((result, error) ->
                                self().tell(new TaskComplete(task.entityId()), ActorRef.noSender())));
    }

    record Task<R>(EntityId entityId, Supplier<CompletionStage<R>> taskRunner) {}

    record TaskResult<R>(@Nullable R result, @Nullable Throwable error) {}

    private record TaskComplete(EntityId entityId) {}

}
