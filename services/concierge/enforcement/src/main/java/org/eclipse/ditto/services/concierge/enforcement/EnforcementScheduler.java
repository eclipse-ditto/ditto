/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.enforcement;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Actor that schedules enforcement with timeout.
 */
final class EnforcementScheduler extends AbstractActor {

    /**
     * The name of this actor under the parent actor, which should be EnforcerActor.
     */
    static final String ACTOR_NAME = "scheduler";

    private final Map<EntityId, Futures> futuresMap;
    private final DittoDiagnosticLoggingAdapter log;

    private EnforcementScheduler() {
        futuresMap = new HashMap<>();
        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
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
            if (entityId.isDummy()) {
                // This should not happen: Refuse to perform enforcement task for messages without ID.
                log.error("EnforcementTaskWithoutEntityId <{}>", task);
                return null;
            } else {
                log.debug("Scheduling <{}> at <{}>", task, cachedFutures);
                final Futures previousFutures = cachedFutures != null ? cachedFutures : Futures.initial();
                return scheduleTaskAfter(previousFutures, task);
            }
        });
    }

    private void futureComplete(final FutureComplete futureComplete) {
        log.debug("Got <{}>", futureComplete);
        futuresMap.computeIfPresent(futureComplete.entityId, (entityId, futures) -> {
            log.debug("Reducing reference count <{}>", futures);
            return futures.onComplete();
        });
    }

    private void dispatchEnforcedMessage(final Contextual<?> enforcementResult) {
        final DittoDiagnosticLoggingAdapter logger = enforcementResult.getLog();
        logger.setCorrelationId(enforcementResult.getMessage());
        final Optional<ActorRef> receiverOpt = enforcementResult.getReceiver();
        final Optional<Supplier<CompletionStage<Object>>> askFutureOpt = enforcementResult.getAskFuture();
        if (askFutureOpt.isPresent() && receiverOpt.isPresent()) {
            final ActorRef receiver = receiverOpt.get();
            logger.debug("About to pipe contextual message <{}> after ask-step to receiver: <{}>",
                    enforcementResult.getMessage(), receiver);
            // It does not disrupt command order guarantee to run the ask-future here if the ask-future
            // is initiated by a call to Patterns.ask(), because Patterns.ask() calls ActorRef.tell()
            // in the calling thread.
            Patterns.pipe(askFutureOpt.get().get(), getContext().dispatcher()).to(receiver);
        } else if (receiverOpt.isPresent()) {
            final ActorRef receiver = receiverOpt.get();
            final Object wrappedMsg =
                    enforcementResult.getReceiverWrapperFunction().apply(enforcementResult.getMessage());
            logger.debug("About to send contextual message <{}> to receiver: <{}>", wrappedMsg, receiver);
            receiver.tell(wrappedMsg, enforcementResult.getSender());
        } else {
            logger.debug("No receiver found in Contextual - as a result just ignoring it: <{}>", enforcementResult);
        }
        logger.discardCorrelationId();
    }

    private Futures scheduleTaskAfter(final Futures previousFutures, final EnforcementTask task) {
        final CompletionStage<?> taskFuture = previousFutures.authFuture.thenCompose(authChangeComplete ->
                previousFutures.enforceFuture.thenCombine(task.start(), (previousTaskComplete, enforcementResult) -> {
                    dispatchEnforcedMessage(enforcementResult);
                    sendFutureComplete(task);
                    return null;
                })
        );
        return task.changesAuthorization()
                ? previousFutures.appendAuthFuture(taskFuture)
                : previousFutures.appendEnforceFuture(taskFuture);
    }

    private void sendFutureComplete(final EnforcementTask task) {
        getSelf().tell(FutureComplete.of(task.getEntityId()), ActorRef.noSender());
    }

    private static final class FutureComplete {

        final EntityId entityId;

        private FutureComplete(final EntityId entityId) {
            this.entityId = entityId;
        }

        private static FutureComplete of(final EntityId entityId) {
            return new FutureComplete(entityId);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[entityId=" + entityId + "]";
        }
    }

    private static final class Futures {

        private static final Futures INITIAL_FUTURES =
                new Futures(CompletableFuture.completedStage(null), CompletableFuture.completedStage(null), 0);

        private final CompletionStage<?> authFuture;
        private final CompletionStage<?> enforceFuture;
        private final int referenceCount;

        private Futures(final CompletionStage<?> authFuture,
                final CompletionStage<?> enforceFuture, final int referenceCount) {
            this.authFuture = authFuture;
            this.enforceFuture = enforceFuture;
            this.referenceCount = referenceCount;
        }

        private static Futures initial() {
            return INITIAL_FUTURES;
        }

        private Futures appendAuthFuture(final CompletionStage<?> authFuture) {
            return new Futures(authFuture, authFuture, referenceCount + 1);
        }

        private Futures appendEnforceFuture(final CompletionStage<?> enforceFuture) {
            return new Futures(authFuture, enforceFuture, referenceCount + 1);
        }

        @Nullable
        private Futures onComplete() {
            final int nextReference = referenceCount - 1;
            if (nextReference <= 0) {
                return null;
            } else {
                return new Futures(authFuture, enforceFuture, nextReference);
            }
        }

        @Override
        public String toString() {
            return String.format("%d Futures", referenceCount);
        }
    }
}
