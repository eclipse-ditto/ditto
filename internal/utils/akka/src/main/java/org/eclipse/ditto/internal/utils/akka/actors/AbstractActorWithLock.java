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
package org.eclipse.ditto.internal.utils.akka.actors;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;

import javax.annotation.Nullable;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;

/**
 * Actor that can prevent itself from handling messages for a period of time.
 */
public abstract class AbstractActorWithLock extends AbstractActorWithStashWithTimers {

    private boolean locked;
    @Nullable private Cancellable lockTimeout;

    protected AbstractActorWithLock() {
        locked = false;
        lockTimeout = null;
    }

    /**
     * @return Actor's usual message handler. It will always be invoked in the actor's thread.
     */
    protected abstract Receive handleMessage();

    /**
     * Prevents this actor from handling messages until it gets unlocked.
     * <p>
     * <em>This method is NOT thread-safe and MUST be called in the actor's thread!</em>
     * </p>
     *
     * @param maxLockTime the maximum time the actor stays locked.
     * @throws NullPointerException if {@code maxLockTime} is {@code null}.
     */
    protected void setLocked(final Duration maxLockTime) {
        checkNotNull(maxLockTime, "maxLockTime");
        locked = true;
        cancelLockTimeout();
        final ActorContext context = getContext();
        lockTimeout = context.system()
                .scheduler()
                .scheduleOnce(maxLockTime, getSelf(), Control.UNLOCK, context.dispatcher(), ActorRef.noSender());
    }

    /**
     * Unlocks this actor to handle messages again.
     * <p>
     * <em>This method is thread-safe and can be called anywhere.</em>
     * </p>
     */
    protected void unlock() {
        getSelf().tell(Control.UNLOCK, ActorRef.noSender());
    }

    /**
     * Switches the actor's message handler.
     * <p>
     * <em>DO NOT call {@code getContext().become()} directly; otherwise the actor loses the ability to lock
     * itself.</em>
     * </p>
     *
     * @param receive the new message handler.
     */
    protected void become(final Receive receive) {
        getContext().become(lockBehavior(receive));
    }

    private Receive lockBehavior(final Receive receive) {
        checkNotNull(receive, "actor's message handler");
        return ReceiveBuilder.create()
                .matchEquals(Control.UNLOCK, unlock -> {
                    locked = false;
                    cancelLockTimeout();
                    unstashAll();
                })
                .matchAny(message -> {
                    final PartialFunction<Object, ?> handler = receive.onMessage();
                    if (locked) {
                        stash();
                    } else if (handler.isDefinedAt(message)) {
                        handler.apply(message);
                    } else {
                        unhandled(message);
                    }
                })
                .build();
    }

    /**
     * Switches the actor's message handler.
     * <p>
     * <em>DO NOT call {@code getContext().become()} directly; otherwise the actor loses the ability to lock
     * itself.</em>
     * </p>
     *
     * @param receive the new message handler.
     * @param discardOld whether the old handler should be discarded.
     */
    protected void become(final Receive receive, final boolean discardOld) {
        getContext().become(lockBehavior(receive), discardOld);
    }

    @Override
    public final Receive createReceive() {
        return lockBehavior(handleMessage());
    }

    @Override
    public void postStop() throws Exception {
        cancelLockTimeout();
        super.postStop();
    }

    private void cancelLockTimeout() {
        if (null != lockTimeout) {
            lockTimeout.cancel();
            lockTimeout = null;
        }
    }

    private enum Control {
        UNLOCK
    }

}
