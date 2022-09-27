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
package org.eclipse.ditto.internal.utils.akka.actors;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;

/**
 * Actor that handles commands during graceful shutdown.
 */
public abstract class AbstractActorWithShutdownBehavior extends AbstractActor {

    /**
     * Ask-timeout in shutdown tasks. Its duration should be long enough but ultimately does not
     * matter because each shutdown phase has its own timeout.
     */
    public static final Duration SHUTDOWN_ASK_TIMEOUT = Duration.ofMinutes(2L);

    protected AbstractActorWithShutdownBehavior() {}

    /**
     * @return Actor's usual message handler. It will always be invoked in the actor's thread.
     */
    protected abstract Receive handleMessage();

    /**
     * Handles the service unbind .
     */
    protected abstract void serviceUnbind(final Control serviceUnbind);

    /**
     * Handles waiting for ongoing requests.
     */
    protected abstract void serviceRequestsDone(final Control serviceRequestsDone);

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
        getContext().become(shutdownBehavior(receive));
    }

    private Receive shutdownBehavior(final Receive receive) {
        checkNotNull(receive, "actor's message handler");
        return ReceiveBuilder.create()
                .matchEquals(Control.SERVICE_UNBIND, this::serviceUnbind)
                .matchEquals(Control.SERVICE_REQUESTS_DONE, this::serviceRequestsDone)
                .matchAny(message -> {
                    final PartialFunction<Object, ?> handler = receive.onMessage();
                    if (handler.isDefinedAt(message)) {
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
        getContext().become(shutdownBehavior(receive), discardOld);
    }

    @Override
    public final Receive createReceive() {
        return shutdownBehavior(handleMessage());
    }

    public enum Control {
        SERVICE_UNBIND,

        SERVICE_REQUESTS_DONE,

        OP_COMPLETE
    }

}
