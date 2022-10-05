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

import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.Done;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;

/**
 * Actor that handles commands during graceful shutdown.
 */
public abstract class AbstractActorWithShutdownBehaviorAndRequestCounting extends AbstractActorWithShutdownBehavior {

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private int ongoingRequests = 0;
    @Nullable private ActorRef shutdownReceiver = null;

    @Override
    public final Receive createReceive() {
        return shutdownBehavior(requestCountingBehavior(handleMessage()));
    }

    @Override
    public void serviceRequestsDone(final Control serviceRequestsDone) {
        if (ongoingRequests == 0) {
            log.info("{}: no ongoing requests", serviceRequestsDone);
            getSender().tell(Done.getInstance(), getSelf());
        } else {
            log.info("{}: waiting for {} ongoing requests", serviceRequestsDone, ongoingRequests);
            shutdownReceiver = getSender();
        }
    }

    /**
     * Switches the actor's message handler.
     *
     * @param receive the new message handler.
     */
    @Override
    protected void become(final Receive receive) {
        getContext().become(shutdownBehavior(requestCountingBehavior(receive)));
    }

    private Receive requestCountingBehavior(final Receive receive) {
        checkNotNull(receive, "actor's message handler");
        return ReceiveBuilder.create()
                .matchEquals(ControlOp.OP_COMPLETE, this::decrementRequestCounter)
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

    public void withRequestCounting(final CompletionStage<?> requestRunner) {
        ++ongoingRequests;
        requestRunner.whenComplete(this::requestComplete);
    }

    private void requestComplete(@Nullable final Object result, final Throwable error) {
        log.debug("Request completed - send {} to myself", ControlOp.OP_COMPLETE);
        getSelf().tell(ControlOp.OP_COMPLETE, ActorRef.noSender());
    }

    private void decrementRequestCounter(final ControlOp decrementOpCounter) {
        --ongoingRequests;
        if (ongoingRequests == 0 && shutdownReceiver != null) {
            log.info("{}: finished waiting for requests", decrementOpCounter);
            shutdownReceiver.tell(Done.done(), getSelf());
        } else {
            log.debug("{}: waiting for {} request(s) to complete", decrementOpCounter, ongoingRequests);
        }
    }

    public enum ControlOp {
        OP_COMPLETE
    }

}
