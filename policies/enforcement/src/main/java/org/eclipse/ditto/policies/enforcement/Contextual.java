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

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Scheduler;

/**
 * A message together with contextual information about the actor processing it.
 * TODO CR-11297 candidate for removal
 */
public final class Contextual<T extends WithDittoHeaders> implements WithSender<T>, WithDittoHeaders {

    private static final String POLICY_ENFORCER_CACHE_DISPATCHER = "enforcement-cache-dispatcher";

    @Nullable private final T message;
    private final ActorRef self;
    private final ActorRef sender;
    private final Scheduler scheduler;
    private final Executor executor;
    private final ActorRef pubSubMediator;
    private final ActorRef commandForwarder;
    private final AskWithRetryConfig askWithRetryConfig;
    private final ThreadSafeDittoLoggingAdapter log;

    @Nullable private final EnforcementCacheKey cacheKey;
    @Nullable private final ActorRef receiver;
    @Nullable private final Function<Object, Object> receiverWrapperFunction;
    @Nullable private final Supplier<CompletionStage<Object>> askFuture;

    private Contextual(@Nullable final T message,
            final ActorRef self,
            @Nullable final ActorRef sender,
            final Scheduler scheduler,
            final Executor executor,
            final ActorRef pubSubMediator,
            final ActorRef commandForwarder,
            final AskWithRetryConfig askWithRetryConfig,
            final ThreadSafeDittoLoggingAdapter log,
            @Nullable final EnforcementCacheKey cacheKey,
            @Nullable final ActorRef receiver,
            @Nullable final Function<Object, Object> receiverWrapperFunction,
            @Nullable final Supplier<CompletionStage<Object>> askFuture) {

        this.message = message;
        this.self = self;
        this.sender = sender;
        this.scheduler = scheduler;
        this.executor = executor;
        this.pubSubMediator = pubSubMediator;
        this.commandForwarder = commandForwarder;
        this.askWithRetryConfig = askWithRetryConfig;
        this.log = log;
        this.cacheKey = cacheKey;
        this.receiver = receiver;
        this.receiverWrapperFunction = receiverWrapperFunction;
        this.askFuture = askFuture;
    }

    static <T extends WithDittoHeaders> Contextual<T> forActor(final ActorRef self,
            final ActorSystem actorSystem,
            final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final AskWithRetryConfig askWithRetryConfig,
            final ThreadSafeDittoLoggingAdapter log) {

        return new Contextual<>(null,
                self,
                actorSystem.deadLetters(),
                actorSystem.getScheduler(),
                actorSystem.dispatchers().lookup(POLICY_ENFORCER_CACHE_DISPATCHER),
                pubSubMediator,
                conciergeForwarder,
                askWithRetryConfig,
                log,
                null,
                null,
                null,
                null);
    }

    /**
     * Perform Ask-steps before forwarding a message.
     * {@code Patterns.ask()} has the same command order guarantee as {@code ActorRef.tell()} when executed inside
     * an actor's {@code Receive}, because it eventually calls {@code ActorRef.tell()} in the calling thread.
     *
     * @param askFuture future of a message to forward to the receiver.
     * @return a copy of this with an ask-future.
     */
    Contextual<T> withAskFuture(final Supplier<CompletionStage<Object>> askFuture) {
        return new Contextual<>(message,
                self,
                sender,
                scheduler,
                executor,
                pubSubMediator,
                commandForwarder,
                askWithRetryConfig,
                log,
                cacheKey,
                receiver,
                receiverWrapperFunction,
                askFuture);
    }

    /**
     * Retrieve the message but tolerate that it may be null.
     *
     * @return the optional message.
     */
    public Optional<T> getMessageOptional() {
        return Optional.ofNullable(message);
    }

    @Override
    public T getMessage() {
        if (message == null) {
            throw new IllegalStateException("Contextual: message was null where it should not have been");
        }
        return message;
    }

    @Override
    public ActorRef getSender() {
        return sender;
    }

    /**
     * Returns the scheduler to use for doing retries of asks.
     *
     * @return the scheduler to use for doing retries of asks.
     */
    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Returns the executor to use for doing asks/retries of asks.
     *
     * @return the executor to use for doing asks/retries of asks.
     */
    public Executor getExecutor() {
        return executor;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        if (message != null) {
            return message.getDittoHeaders();
        } else {
            return DittoHeaders.empty();
        }
    }

    @Override
    public Contextual<T> withMessage(@Nullable final T message) {
        return withReceivedMessage(message, sender);
    }

    <S extends WithDittoHeaders> Contextual<S> setMessage(@Nullable final S message) {
        return withReceivedMessage(message, sender);
    }

    Optional<Supplier<CompletionStage<Object>>> getAskFuture() {
        return Optional.ofNullable(askFuture);
    }

    ActorRef getSelf() {
        return self;
    }

    ActorRef getPubSubMediator() {
        return pubSubMediator;
    }

    ActorRef getCommandForwarder() {
        return commandForwarder;
    }

    AskWithRetryConfig getAskWithRetryConfig() {
        return askWithRetryConfig;
    }

    ThreadSafeDittoLoggingAdapter getLog() {
        return log;
    }

    EnforcementCacheKey getCacheKey() {
        if (cacheKey == null) {
            throw new IllegalStateException("Contextual: cacheKey was null where it should not have been");
        }
        return cacheKey;
    }

    Optional<ActorRef> getReceiver() {
        return Optional.ofNullable(receiver);
    }

    Function<Object, Object> getReceiverWrapperFunction() {
        return receiverWrapperFunction != null ? receiverWrapperFunction : Function.identity();
    }

    <S extends WithDittoHeaders> Optional<Contextual<S>> tryToMapMessage(final Function<T, Optional<S>> f) {
        return f.apply(getMessage()).map(result -> withReceivedMessage(result, sender));
    }

    <S extends WithDittoHeaders> Contextual<S> withReceivedMessage(@Nullable final S message,
            @Nullable final ActorRef sender) {

        return new Contextual<>(message,
                self,
                sender,
                scheduler,
                executor,
                pubSubMediator,
                commandForwarder,
                askWithRetryConfig,
                log,
                cacheKeyFor(message),
                receiver,
                receiverWrapperFunction,
                askFuture);
    }

    public Contextual<T> withReceiver(@Nullable final ActorRef receiver) {
        return new Contextual<>(message,
                self,
                sender,
                scheduler,
                executor,
                pubSubMediator,
                commandForwarder,
                askWithRetryConfig,
                log,
                cacheKey,
                receiver,
                receiverWrapperFunction,
                askFuture);
    }

    Contextual<T> withReceiverWrapperFunction(final UnaryOperator<Object> receiverWrapperFunction) {
        return new Contextual<>(message,
                self,
                sender,
                scheduler,
                executor,
                pubSubMediator,
                commandForwarder,
                askWithRetryConfig,
                log,
                cacheKey,
                receiver,
                receiverWrapperFunction,
                askFuture);
    }

    public Contextual<T> withAskWithRetryConfig(final AskWithRetryConfig askWithRetryConfig) {
        return new Contextual<>(message,
                self,
                sender,
                scheduler,
                executor,
                pubSubMediator,
                commandForwarder,
                askWithRetryConfig,
                log,
                cacheKey,
                receiver,
                receiverWrapperFunction,
                askFuture);
    }

    @Nullable
    private static EnforcementCacheKey cacheKeyFor(@Nullable final WithDittoHeaders signal) {
        if (signal == null) {
            return null;
        } else if (signal instanceof DittoRuntimeException) {
            return null;
        } else {
            return WithEntityId.getEntityIdOfType(EntityId.class, signal)
                    .map(EnforcementCacheKey::of)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Contextual: processed WithDittoHeaders message did not implement " +
                                    "WithResource or WithEntityId: " + signal.getClass().getSimpleName()));
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "message=" + message +
                ", self=" + self +
                ", sender=" + sender +
                ", executionContext=" + executor +
                ", cacheKey=" + cacheKey +
                ", receiver=" + receiver +
                ", receiverWrapperFunction=" + receiverWrapperFunction +
                "]";
    }

}
