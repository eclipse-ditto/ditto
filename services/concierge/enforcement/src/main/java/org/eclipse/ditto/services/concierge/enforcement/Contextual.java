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

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.EntityId;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.base.WithResource;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;

/**
 * A message together with contextual information about the actor processing it.
 */
public final class Contextual<T extends WithDittoHeaders> implements WithSender<T> {

    @Nullable
    private final T message;

    private final ActorRef self;

    private final ActorRef sender;

    private final ActorRef pubSubMediator;

    private final ActorRef conciergeForwarder;

    private final Executor enforcerExecutor;

    private final Duration askTimeout;

    private final DiagnosticLoggingAdapter log;

    @Nullable
    private final EntityId entityId;

    @Nullable
    private final StartedTimer startedTimer;

    // for live signal enforcement
    private final Cache<String, ActorRef> responseReceivers;

    Contextual(@Nullable final T message, final ActorRef self, final ActorRef sender,
            final ActorRef pubSubMediator, final ActorRef conciergeForwarder,
            final Executor enforcerExecutor, final Duration askTimeout, final DiagnosticLoggingAdapter log,
            @Nullable final EntityId entityId,
            @Nullable final StartedTimer startedTimer,
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
        this.startedTimer = startedTimer;
        this.responseReceivers = responseReceivers;
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
    public <S extends WithDittoHeaders> Contextual<S> withMessage(final S message) {
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
        if (entityId == null) {
            throw new IllegalStateException("Contextual: entityId was null where it should not have been");
        }
        return entityId;
    }

    Optional<StartedTimer> getStartedTimer() {
        return Optional.ofNullable(startedTimer);
    }

    Cache<String, ActorRef> getResponseReceivers() {
        return responseReceivers;
    }

    <S extends WithDittoHeaders> Optional<Contextual<S>> tryToMapMessage(final Function<T, Optional<S>> f) {
        return f.apply(getMessage()).map(this::withMessage);
    }

    <S extends WithDittoHeaders> Contextual<S> withReceivedMessage(final S message, final ActorRef sender) {
        return new Contextual<>(message, self, sender, pubSubMediator, conciergeForwarder, enforcerExecutor, askTimeout,
                log, entityIdFor(message), startedTimer, responseReceivers);
    }

    Contextual<T> withTimer(final StartedTimer startedTimer) {
        return new Contextual<>(message, self, sender, pubSubMediator, conciergeForwarder, enforcerExecutor, askTimeout,
                log, entityId, startedTimer, responseReceivers);
    }

    @Nullable
    private static EntityId entityIdFor(@Nullable final WithDittoHeaders<?> signal) {

        if (signal == null) {
            return null;
        }
        else if (signal instanceof DittoRuntimeException) {
            return null;
        }
        else if (signal instanceof WithResource && signal instanceof WithId) {
            final EntityId entityId;
            if (MessageCommand.RESOURCE_TYPE.equals(((WithResource) signal).getResourceType())) {
                entityId = EntityId.of(ThingCommand.RESOURCE_TYPE, ((WithId) signal).getId());
            } else {
                entityId = EntityId.of(((WithResource) signal).getResourceType(), ((WithId) signal).getId());
            }
            return entityId;
        } else {
            throw new IllegalArgumentException("Contextual: processed WithDittoHeaders message did not implement " +
                    "WithResource or WithId: " + signal.getClass().getSimpleName());
        }
    }

    @Override
    public String toString() {
        return String.format(
                "Contextual[message=%s,self=%s,sender=%s,pubSubMediator=%s,conciergeForwarder=%s,entityId=%s]",
                message,
                self,
                sender,
                pubSubMediator,
                conciergeForwarder,
                entityId);
    }
}
