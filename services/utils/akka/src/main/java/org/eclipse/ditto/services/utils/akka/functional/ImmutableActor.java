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
package org.eclipse.ditto.services.utils.akka.functional;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

// TODO: javadoc
public final class ImmutableActor extends AbstractActor {

    private final Total<ActorRef, Total<Object, ?>> messageHandler;

    private ImmutableActor(final Total<ActorUtils, Total<ActorRef, AsyncPartial<Object, ?>>> handlerProvider) {
        final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
        final ActorUtils actorUtils = new ActorUtils(getContext(), log);
        messageHandler = handlerProvider.apply(actorUtils).then(partialMessageHandler ->
                partialMessageHandler.withDefault(message -> {
                    log.warning("Unhandled message <{}>", message);
                    unhandled(message);
                    return null;
                }));
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchAny(message -> messageHandler.apply(getSender()).apply(message))
                .build();
    }

    @FunctionalInterface
    public interface Builder<C, X, Y>
            extends Total<C, Total<ActorUtils, Total<ActorRef, AsyncPartial<X, Y>>>> {

        default ImmutableActor build(final Class<X> clazz, final C configuration) {
            return new ImmutableActor(actorUtils -> {
                final Total<ActorRef, AsyncPartial<X, Y>> messageHandler = apply(configuration).apply(actorUtils);
                return sender -> messageHandler.apply(sender).filterBy(clazz);
            });
        }

        default <Z> Builder<C, Z, Y> as(final Class<X> clazz) {
            return configuration -> actorUtils -> sender ->
                    apply(configuration).apply(actorUtils).apply(sender).filterBy(clazz);

        }

        default Builder<C, X, Y> orElse(final Builder<C, X, Y> that) {
            return configuration -> actorUtils -> {
                final Total<ActorRef, AsyncPartial<X, Y>>
                        thisHandler = this.apply(configuration).apply(actorUtils),
                        thatHandler = that.apply(configuration).apply(actorUtils);

                return sender -> thisHandler.apply(sender).orElse(thatHandler.apply(sender));
            };
        }

        default <Z> Builder<C, X, Z> then(final Builder<C, Y, Z> next) {
            return configuration -> actorUtils -> {
                final Total<ActorRef, AsyncPartial<X, Y>>
                        thisHandler = this.apply(configuration).apply(actorUtils);
                final Total<ActorRef, AsyncPartial<Y, Z>>
                        nextHandler = next.apply(configuration).apply(actorUtils);

                return sender -> thisHandler.apply(sender).then(nextHandler.apply(sender));
            };
        }

        static <C, X, Y> Builder<C, X, Y> empty() {
            return configuration -> actorUtils -> sender -> message ->
                    CompletableFuture.completedFuture(Optional.empty());
        }
    }

    public static final class ActorUtils {

        private final ActorContext context;
        private final DiagnosticLoggingAdapter log;

        private ActorUtils(final ActorContext context, final DiagnosticLoggingAdapter log) {
            this.context = context;
            this.log = log;
        }

        public ActorContext context() {
            return context;
        }

        public DiagnosticLoggingAdapter log() {
            return log;
        }
    }
}
