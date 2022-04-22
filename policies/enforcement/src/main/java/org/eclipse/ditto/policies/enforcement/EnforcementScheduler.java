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
package org.eclipse.ditto.policies.enforcement;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Actor that schedules enforcement tasks. Relying on the inherent timeout of enforcement tasks to not leak memory.
 * TODO TJ candidate for removal
 */
final class EnforcementScheduler extends AbstractActor {

    /**
     * The name of this actor under the parent actor, which should be EnforcerActor.
     */
    static final String ACTOR_NAME = "scheduler";

    /**
     * Cache of started enforcement tasks for each entity ID.
     */
    private final Map<EntityId, Futures> futuresMap;
    private final DittoDiagnosticLoggingAdapter log;
    private final Counter scheduledEnforcementTasks;
    private final Counter completedEnforcementTasks;

    private EnforcementScheduler() {
        futuresMap = new HashMap<>();
        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        scheduledEnforcementTasks = DittoMetrics.counter("scheduled_enforcement_tasks");
        completedEnforcementTasks = DittoMetrics.counter("completed_enforcement_tasks");
    }

    static Props props() {
        return Props.create(EnforcementScheduler.class);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(EnforcementTask.class, this::scheduleEnforcement)
                .match(FutureComplete.class, this::futureComplete)
                .matchAny(message -> log.warning("UnknownMessage <{}>", message))
                .build();
    }

    private void scheduleEnforcement(final EnforcementTask task) {
        futuresMap.compute(task.getEntityId(), (entityId, cachedFutures) -> {
            log.debug("Scheduling <{}> at <{}>", task, cachedFutures);
            final Futures previousFutures = cachedFutures != null ? cachedFutures : Futures.initial();
            return scheduleTaskAfter(previousFutures, task);
        });
        scheduledEnforcementTasks.increment();
    }

    private void futureComplete(final FutureComplete futureComplete) {
        log.debug("Got <{}>", futureComplete);
        futureComplete.getError().ifPresent(error -> log.error(error, "FutureFailed <{}>", futureComplete));
        futuresMap.computeIfPresent(futureComplete.entityId, (entityId, futures) -> {
            log.debug("Reducing reference count <{}>", futures);
            return futures.onComplete();
        });
        completedEnforcementTasks.increment();
    }

    private Void dispatchEnforcedMessage(final Contextual<?> enforcementResult) {
        final Optional<? extends WithDittoHeaders> messageOpt = enforcementResult.getMessageOptional();
        if (messageOpt.isPresent()) {
            final WithDittoHeaders message = messageOpt.get();
            final ThreadSafeDittoLoggingAdapter logger = enforcementResult.getLog().withCorrelationId(message);
            final Optional<ActorRef> receiverOpt = enforcementResult.getReceiver();
            final Optional<Supplier<CompletionStage<Object>>> askFutureOpt = enforcementResult.getAskFuture();
            if (askFutureOpt.isPresent() && receiverOpt.isPresent()) {
                final ActorRef receiver = receiverOpt.get();
                logger.debug("About to pipe contextual message <{}> after ask-step to receiver: <{}>",
                        message, receiver);
                // It does not disrupt command order guarantee to run the ask-future here if the ask-future
                // is initiated by a call to Patterns.ask(), because Patterns.ask() calls ActorRef.tell()
                // in the calling thread.
                Patterns.pipe(askFutureOpt.get().get(), getContext().dispatcher()).to(receiver);
            } else if (receiverOpt.isPresent()) {
                final ActorRef receiver = receiverOpt.get();
                final Object wrappedMsg =
                        enforcementResult.getReceiverWrapperFunction().apply(message);
                logger.debug("About to send contextual message <{}> to receiver: <{}>", wrappedMsg, receiver);
                receiver.tell(wrappedMsg, enforcementResult.getSender());
            } else {
                logger.debug("No receiver found in Contextual - as a result just ignoring it: <{}>", enforcementResult);
            }
            logger.discardCorrelationId();
        } else {
            // message does not exist; nothing to dispatch
            enforcementResult.getLog().debug("Not dispatching due to lack of message: {}", enforcementResult);
        }

        return null;
    }

    /**
     * Schedule an enforcement task based on previous futures of an entity such that enforcement task does not start
     * until all previous authorization changes are complete and does not complete until all previous tasks are
     * complete.
     *
     * @param previousFutures in-flight enforcement tasks for the same entity.
     * @param task the task to schedule.
     * @return the next in-flight enforcement tasks, including the scheduled task.
     */
    private Futures scheduleTaskAfter(final Futures previousFutures, final EnforcementTask task) {
        final CompletionStage<?> taskFuture =
                previousFutures.beforeStartFuture.thenCompose(authChangeComplete ->
                        previousFutures.beforeCompleteFuture.thenCombine(task.start(),
                                (previousTaskComplete, enforcementResult) -> dispatchEnforcedMessage(enforcementResult)
                        )
                ).handle((result, error) -> sendFutureComplete(task, error));
        return task.changesAuthorization()
                ? previousFutures.appendBeforeStartFuture(taskFuture)
                : previousFutures.appendBeforeCompleteFuture(taskFuture);
    }

    private Void sendFutureComplete(final EnforcementTask task, @Nullable final Throwable error) {
        getSelf().tell(FutureComplete.of(task.getEntityId(), error), ActorRef.noSender());
        return null;
    }

    /**
     * Self-sent event to signal completion of an enforcement task for cache maintenance.
     */
    private static final class FutureComplete {

        private final EntityId entityId;
        @Nullable private final Throwable error;

        private FutureComplete(final EntityId entityId, @Nullable final Throwable error) {
            this.entityId = entityId;
            this.error = error;
        }

        private static FutureComplete of(final EntityId entityId, @Nullable final Throwable error) {
            return new FutureComplete(entityId, error);
        }

        private Optional<Throwable> getError() {
            return Optional.ofNullable(error);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[entityId=" + entityId + "]";
        }
    }

    /**
     * Cache entry for 1 entity including: its last scheduled authorization-changing task, its last scheduled
     * non-authorization-changing task, and the amount of in-flight enforcement tasks.
     */
    private static final class Futures {

        private static final Futures INITIAL_FUTURES =
                new Futures(CompletableFuture.completedStage(null), CompletableFuture.completedStage(null), 0);

        private final CompletionStage<?> beforeStartFuture;
        private final CompletionStage<?> beforeCompleteFuture;
        private final int referenceCount;

        private Futures(final CompletionStage<?> beforeStartFuture, final CompletionStage<?> beforeCompleteFuture,
                final int referenceCount) {

            this.beforeStartFuture = beforeStartFuture;
            this.beforeCompleteFuture = beforeCompleteFuture;
            this.referenceCount = referenceCount;
        }

        /**
         * @return the initial future of all entities: all tasks complete; 0 task in-flight.
         */
        private static Futures initial() {
            return INITIAL_FUTURES;
        }

        private Futures appendBeforeStartFuture(final CompletionStage<?> beforeStartFuture) {

            // Setting both futures to the specified future allows the garbage collector to discard the unused
            // beforeCompleteFuture object.
            return new Futures(beforeStartFuture, beforeStartFuture, referenceCount + 1);
        }

        private Futures appendBeforeCompleteFuture(final CompletionStage<?> beforeCompleteFuture) {
            return new Futures(beforeStartFuture, beforeCompleteFuture, referenceCount + 1);
        }

        @Nullable
        private Futures onComplete() {
            final int nextReferenceCount = referenceCount - 1;
            if (nextReferenceCount <= 0) {
                return null;
            } else {
                return new Futures(beforeStartFuture, beforeCompleteFuture, nextReferenceCount);
            }
        }

        @Override
        public String toString() {
            return String.format("%d Futures", referenceCount);
        }

    }

}
