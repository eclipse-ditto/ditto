/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.akka;

import java.time.Duration;

import javax.annotation.Nullable;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;

/**
 * Actor that can prevent itself from handling messages for a period of time.
 */
public abstract class AbstractActorWithLock extends AbstractActorWithStash {

    private boolean isLocked = false;

    @Nullable
    private Cancellable lockTimeout = null;

    /**
     * @return Actor's usual message handler. It will always be invoked in the actor's thread.
     */
    protected abstract Receive handleMessage();

    /**
     * @return Maximum time the actor stays locked.
     */
    @Nullable
    protected abstract Duration maxLockTime();

    /**
     * Prevent this actor from handling messages until unlocked - NOT thread-safe; MUST be called in the actor's thread.
     */
    protected void setLocked() {
        isLocked = true;
        final Duration timeout = maxLockTime();
        if (timeout != null) {
            cancelLockTimeout();
            lockTimeout = getContext().system()
                    .scheduler()
                    .scheduleOnce(timeout, getSelf(), Control.UNLOCK, getContext().dispatcher(), ActorRef.noSender());
        }
    }

    /**
     * Unlock this actor to handle messages again - thread-safe; can be called anywhere.
     */
    protected void unlock() {
        getSelf().tell(Control.UNLOCK, ActorRef.noSender());
    }

    /**
     * Switch the actor's message handler - DO NOT call {@code getContext().become()} directly; otherwise the actor
     * loses the ability to lock itself.
     *
     * @param receive the new message handler.
     */
    protected void become(final Receive receive) {
        getContext().become(lockBehavior(receive));
    }

    /**
     * Switch the actor's message handler - DO NOT call {@code getContext().become()} directly; otherwise the actor
     * loses the ability to lock itself.
     *
     * @param receive the new message handler.
     * @param discardOld whether the old handler should be discarded.
     */
    protected void become(final Receive receive, final boolean discardOld) {
        getContext().become(lockBehavior(receive), discardOld);
    }

    private Receive lockBehavior(final Receive receive) {
        final PartialFunction<Object, ?> handler = receive.onMessage();
        return ReceiveBuilder.create()
                .matchEquals(Control.UNLOCK, unlock -> {
                    isLocked = false;
                    cancelLockTimeout();
                    unstashAll();
                })
                .matchAny(message -> {
                    if (isLocked) {
                        stash();
                    } else if (handler.isDefinedAt(message)) {
                        handler.apply(message);
                    } else {
                        unhandled(message);
                    }
                })
                .build();
    }

    @Override
    public final Receive createReceive() {
        return lockBehavior(handleMessage());
    }

    @Override
    public void postStop() {
        cancelLockTimeout();
        super.postStop();
    }

    private void cancelLockTimeout() {
        if (lockTimeout != null) {
            lockTimeout.cancel();
            lockTimeout = null;
        }
    }

    private enum Control {
        UNLOCK
    }
}
