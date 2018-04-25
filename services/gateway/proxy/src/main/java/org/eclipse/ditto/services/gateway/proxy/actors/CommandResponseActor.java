/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.gateway.proxy.actors;


import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import scala.concurrent.duration.Duration;

/**
 * This actor waits for a response to a command (with timeout), forwards the response to the original sender and stops
 * itself.
 */
public class CommandResponseActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String correlationId;
    private final ActorRef sender;

    private CommandResponseActor(final String correlationId, final ActorRef sender) {
        this.correlationId = correlationId;
        this.sender = sender;
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param correlationId the correlationId of the command
     * @param sender the sender of the command which must receive the corresponding response
     * @return the Akka configuration Props object
     */
    public static Props props(final String correlationId, final ActorRef sender) {
        return Props.create(CommandResponseActor.class, new Creator<CommandResponseActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public CommandResponseActor create() {
                return new CommandResponseActor(correlationId, sender);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(CommandResponse.class, response -> {
                    LogUtil.enhanceLogWithCorrelationId(log, response);
                    log.debug("Received response of type '{}', forwarding to original sender '{}'.", response.getType(),
                            sender);
                    sender.tell(response, getSender());
                    getContext().stop(getSelf());
                })
                .match(ReceiveTimeout.class, r -> {
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Waiting for a response timed out, stopping myself.");
                    getContext().stop(getSelf());
                })
                .matchAny(msg -> log.warning("Unexpected message, cannot handle: {}", msg.getClass().getName()))
                .build();
    }

    @Override
    public void preStart() {
        getContext().setReceiveTimeout(Duration.apply(5, TimeUnit.MINUTES));
    }
}
