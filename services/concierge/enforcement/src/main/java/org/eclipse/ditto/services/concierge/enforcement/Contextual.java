/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.EntityIdWithResourceType;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.model.base.entity.id.WithEntityId;
import org.eclipse.ditto.signals.base.WithResource;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.actor.ActorRef;

/**
 * A message together with contextual information about the actor processing it.
 */
public final class Contextual<T extends WithDittoHeaders> implements WithSender<T>, WithDittoHeaders {

    @Nullable
    private final T message;

    private final ActorRef self;

    private final ActorRef sender;

    private final ActorRef pubSubMediator;

    private final ActorRef conciergeForwarder;

    private final Duration askTimeout;

    private final ThreadSafeDittoLoggingAdapter log;

    @Nullable
    private final EntityIdWithResourceType entityId;

    @Nullable
    private final StartedTimer startedTimer;

    @Nullable
    private final ActorRef receiver;

    @Nullable
    private final Function<Object, Object> receiverWrapperFunction;

    // for live signal enforcement
    @Nullable
    private final Cache<String, ActorRef> responseReceivers;

    @Nullable
    private final Supplier<CompletionStage<Object>> askFuture;

    private Contextual(@Nullable final T message, final ActorRef self, @Nullable final ActorRef sender,
            final ActorRef pubSubMediator, final ActorRef conciergeForwarder,
            final Duration askTimeout, final ThreadSafeDittoLoggingAdapter log,
            @Nullable final EntityIdWithResourceType entityId,
            @Nullable final StartedTimer startedTimer,
            @Nullable final ActorRef receiver,
            @Nullable final Function<Object, Object> receiverWrapperFunction,
            @Nullable final Cache<String, ActorRef> responseReceivers,
            @Nullable final Supplier<CompletionStage<Object>> askFuture) {
        this.message = message;
        this.self = self;
        this.sender = sender;
        this.pubSubMediator = pubSubMediator;
        this.conciergeForwarder = conciergeForwarder;
        this.askTimeout = askTimeout;
        this.log = log;
        this.entityId = entityId;
        this.startedTimer = startedTimer;
        this.receiver = receiver;
        this.receiverWrapperFunction = receiverWrapperFunction;
        this.responseReceivers = responseReceivers;
        this.askFuture = askFuture;
    }

    static <T extends WithDittoHeaders> Contextual<T> forActor(final ActorRef self,
            final ActorRef deadLetters,
            final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final Duration askTimeout,
            final ThreadSafeDittoLoggingAdapter log,
            @Nullable final Cache<String, ActorRef> responseReceivers) {

        return new Contextual<>(null, self, deadLetters, pubSubMediator, conciergeForwarder, askTimeout, log, null,
                null,
                null, null, responseReceivers, null);
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
        return new Contextual<>(message, self, sender, pubSubMediator, conciergeForwarder, askTimeout, log, entityId,
                startedTimer, receiver, receiverWrapperFunction, responseReceivers, askFuture);
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

    ActorRef getConciergeForwarder() {
        return conciergeForwarder;
    }

    Duration getAskTimeout() {
        return askTimeout;
    }

    ThreadSafeDittoLoggingAdapter getLog() {
        return log;
    }

    EntityIdWithResourceType getEntityIdWithResourceType() {
        if (entityId == null) {
            throw new IllegalStateException("Contextual: entityId was null where it should not have been");
        }
        return entityId;
    }

    Optional<StartedTimer> getStartedTimer() {
        return Optional.ofNullable(startedTimer);
    }

    Optional<ActorRef> getReceiver() {
        return Optional.ofNullable(receiver);
    }

    Function<Object, Object> getReceiverWrapperFunction() {
        return receiverWrapperFunction != null ? receiverWrapperFunction : Function.identity();
    }

    Optional<Cache<String, ActorRef>> getResponseReceivers() {
        return Optional.ofNullable(responseReceivers);
    }

    <S extends WithDittoHeaders> Optional<Contextual<S>> tryToMapMessage(final Function<T, Optional<S>> f) {
        return f.apply(getMessage()).map(result -> withReceivedMessage(result, sender));
    }

    <S extends WithDittoHeaders> Contextual<S> withReceivedMessage(@Nullable final S message,
            @Nullable final ActorRef sender) {
        return new Contextual<>(message, self, sender, pubSubMediator, conciergeForwarder, askTimeout,
                log, entityIdFor(message), startedTimer, receiver, receiverWrapperFunction, responseReceivers,
                askFuture);
    }

    Contextual<T> withTimer(final StartedTimer startedTimer) {
        return new Contextual<>(message, self, sender, pubSubMediator, conciergeForwarder, askTimeout,
                log, entityId, startedTimer, receiver, receiverWrapperFunction, responseReceivers,
                askFuture);
    }

    Contextual<T> withReceiver(@Nullable final ActorRef receiver) {
        return new Contextual<>(message, self, sender, pubSubMediator, conciergeForwarder, askTimeout,
                log, entityId, startedTimer, receiver, receiverWrapperFunction, responseReceivers,
                askFuture);
    }

    Contextual<T> withReceiverWrapperFunction(final Function<Object, Object> receiverWrapperFunction) {
        return new Contextual<>(message, self, sender, pubSubMediator, conciergeForwarder, askTimeout,
                log, entityId, startedTimer, receiver, receiverWrapperFunction, responseReceivers,
                askFuture);
    }

    @Nullable
    private static EntityIdWithResourceType entityIdFor(@Nullable final WithDittoHeaders signal) {

        if (signal == null) {
            return null;
        } else if (signal instanceof DittoRuntimeException) {
            return null;
        } else if (signal instanceof WithResource && signal instanceof WithEntityId) {
            final EntityIdWithResourceType entityId;
            if (MessageCommand.RESOURCE_TYPE.equals(((WithResource) signal).getResourceType())) {
                entityId =
                        EntityIdWithResourceType.of(ThingCommand.RESOURCE_TYPE, ((WithEntityId) signal).getEntityId());
            } else {
                entityId = EntityIdWithResourceType.of(((WithResource) signal).getResourceType(),
                        ((WithEntityId) signal).getEntityId());
            }
            return entityId;
        } else {
            throw new IllegalArgumentException("Contextual: processed WithDittoHeaders message did not implement " +
                    "WithResource or WithEntityId: " + signal.getClass().getSimpleName());
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "message=" + message +
                ", self=" + self +
                ", sender=" + sender +
                ", entityId=" + entityId +
                ", receiver=" + receiver +
                ", receiverWrapperFunction=" + receiverWrapperFunction +
                ", responseReceivers=" + responseReceivers +
                "]";
    }

}
