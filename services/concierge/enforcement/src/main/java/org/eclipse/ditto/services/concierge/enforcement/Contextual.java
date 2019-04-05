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
import java.util.concurrent.Executor;
import java.util.function.Function;

import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.services.utils.cache.Cache;

import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;

/**
 * A message together with contextual information about the actor processing it.
 */
public final class Contextual<T> implements WithSender<T> {

    private final T message;

    private final ActorRef self;

    private final ActorRef sender;

    private final ActorRef pubSubMediator;

    private final ActorRef conciergeForwarder;

    private final Executor enforcerExecutor;

    private final Duration askTimeout;

    private final DiagnosticLoggingAdapter log;

    private final EntityId entityId;

    // for live signal enforcement
    private final Cache<String, ActorRef> responseReceivers;

    Contextual(final T message, final ActorRef self, final ActorRef sender,
            final ActorRef pubSubMediator, final ActorRef conciergeForwarder,
            final Executor enforcerExecutor, final Duration askTimeout, final DiagnosticLoggingAdapter log,
            final EntityId entityId,
            final Cache<String, ActorRef> responseReceivers) {
        this.message = message;
        this.self = self;
        this.sender = sender;
        this.pubSubMediator = pubSubMediator;
        this.conciergeForwarder = conciergeForwarder;
        this.enforcerExecutor = enforcerExecutor;
        this.askTimeout = askTimeout;
        this.log = log;
        this.entityId = entityId;
        this.responseReceivers = responseReceivers;
    }

    @Override
    public T getMessage() {
        return message;
    }

    @Override
    public ActorRef getSender() {
        return sender;
    }

    @Override
    public <S> Contextual<S> withMessage(final S message) {
        return withReceivedMessage(message, sender);
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

    Executor getEnforcerExecutor() {
        return enforcerExecutor;
    }

    Duration getAskTimeout() {
        return askTimeout;
    }

    DiagnosticLoggingAdapter getLog() {
        return log;
    }

    EntityId getEntityId() {
        return entityId;
    }

    Cache<String, ActorRef> getResponseReceivers() {
        return responseReceivers;
    }

    <S> Optional<Contextual<S>> tryToMapMessage(final Function<T, Optional<S>> f) {
        return f.apply(getMessage()).map(this::withMessage);
    }

    <S> Contextual<S> withReceivedMessage(final S message, final ActorRef sender) {
        return new Contextual<>(message, self, sender, pubSubMediator, conciergeForwarder, enforcerExecutor, askTimeout,
                log, entityId, responseReceivers);
    }

    @Override
    public String toString() {
        return String.format(
                "Contextual[message=%s,self=%s,sender=%s,pubSubMediator=%s,conciergeForwarder=%s,entityId=%s]",
                message.toString(),
                self.toString(),
                sender.toString(),
                pubSubMediator.toString(),
                conciergeForwarder.toString(),
                entityId.toString());
    }
}
