/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import java.time.Duration;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

// TODO: comment
// TODO: test
// TODO: configure stash size
// TODO: think about reworkto use a pattern like this: https://manuel.bernhardt.io/2018/03/20/akka-anti-patterns-overusing-actorselection/
// TODO: currently doesn't work with wildcard actor selection. gotta read the docs if it might be possible at all to resolve
//  an wildcard actor selection or if it is only possibly to directly send messages to it ...
public final class ForwardActorWithStrategy extends AbstractActorWithStash {
    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorSelection actorSelection;
    @Nullable
    private ActorRef currentActorRef;

    @SuppressWarnings("unused") // used by akka props
    private ForwardActorWithStrategy(final ActorSelection actorSelection) {
        this.actorSelection = actorSelection;
    }

    static Props forwardTo(final ActorSelection selection) {
        return Props.create(ForwardActorWithStrategy.class, selection);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Terminated.class, this::resetCurrentActorRef)
                .matchAny(this::findActorAndForward)
                .build();
    }

    private void findActorAndForward(final Object message) {
        if (null != currentActorRef) {
            forward(message, currentActorRef);
        }
        // TODO: make timeout configurable
        actorSelection.resolveOne(Duration.ofSeconds(1))
                .thenApply(Optional::of)
                .exceptionally(throwable -> {
                    log.warning("No actor found for actor selection <{}>. Will stash message: {}",
                            actorSelection, message);

                    stash();
                    return Optional.empty();
                })
                .thenAccept(optionalRef -> optionalRef.ifPresent(actorRef -> {
                    setCurrentActorRef(actorRef);
                    forward(message, currentActorRef);
                    unstashAll();
                })).toCompletableFuture();

    }

    private void resetCurrentActorRef(final Terminated terminated) {
        log.info("Child actor with actor selection <{}> terminated. Will try to find it again on the next message.", actorSelection);
        currentActorRef = null;
    }

    private void setCurrentActorRef(final ActorRef newActorRef) {
        currentActorRef = newActorRef;
        // TODO: may not be called inside completionstage oder so, weil nicht thread safe
        getContext().watch(currentActorRef);
    }

    private void forward(final Object message, final ActorRef toActor) {
        toActor.forward(message, getContext());
    }
}
