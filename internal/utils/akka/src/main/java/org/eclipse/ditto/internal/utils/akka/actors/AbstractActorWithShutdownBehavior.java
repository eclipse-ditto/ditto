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
     * Ask-timeout in shutdown tasks. Its duration should be long enough but ultimately does not matter because each
     * shutdown phase has its own timeout.
     */
    public static final Duration SHUTDOWN_ASK_TIMEOUT = Duration.ofMinutes(2L);

    /**
     * @return Actor's usual message handler. It will always be invoked in the actor's thread.
     */
    protected abstract Receive handleMessage();

    /**
     * Handles necessary steps during the {@code CoordinatedShutdown.PhaseServiceUnbind()}.
     */
    protected abstract void serviceUnbind(Control serviceUnbind);

    /**
     * Handles necessary steps during the {@code CoordinatedShutdown.PhaseServiceRequestsDone()}.
     */
    protected abstract void serviceRequestsDone(Control serviceRequestsDone);

    /**
     * Switches the actor's message handler.
     *
     * @param receive the new message handler.
     */
    protected void become(final Receive receive) {
        getContext().become(shutdownBehavior(receive));
    }

    protected Receive shutdownBehavior(final Receive receive) {
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

    @Override
    public Receive createReceive() {
        return shutdownBehavior(handleMessage());
    }

    public enum Control {
        SERVICE_UNBIND,

        SERVICE_REQUESTS_DONE
    }

}
