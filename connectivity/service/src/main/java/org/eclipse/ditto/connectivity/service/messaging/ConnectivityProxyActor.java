/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory.getDiagnosticLoggingAdapter;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.aggregator.ThingsAggregatorProxyActor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Actor which delegates {@link Command}s received in connectivity service to the appropriate receivers in the cluster.
 */
public final class ConnectivityProxyActor extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "connectivityProxyActor";

    private final DittoDiagnosticLoggingAdapter log = getDiagnosticLoggingAdapter(this);

    private final ActorRef commandForwarder;
    private final ActorRef aggregatorProxyActor;

    @SuppressWarnings("unused")
    private ConnectivityProxyActor(final ActorRef commandForwarder) {
        this.commandForwarder = commandForwarder;
        this.aggregatorProxyActor = getContext().actorOf(ThingsAggregatorProxyActor.props(commandForwarder),
                ThingsAggregatorProxyActor.ACTOR_NAME);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param commandForwarder the command forwarder
     * @return the Props object
     */
    public static Props props(final ActorRef commandForwarder) {
        return Props.create(ConnectivityProxyActor.class, commandForwarder);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                // handle RetrieveThings in aggregator actor
                .match(RetrieveThings.class, rt -> {
                    log.withCorrelationId(rt).debug("Passing RetrieveThings to aggregator.");
                    aggregatorProxyActor.forward(rt, getContext());
                })
                // forward all other signals via command forwarder
                .match(Signal.class, signal -> {
                    // This message is important to check if a command is accepted for a specific connection, as this happens
                    // quite a lot this is going to the debug level. Use best with a connection-id filter.
                    log.withCorrelationId(signal).debug("Passing '{}' signal to command forwarder.", signal.getType());
                    commandForwarder.forward(signal, getContext());
                })
                // drop and log
                .matchAny(m -> log.info("unexpected message of type {}", m.getClass().getName()))
                .build();
    }
}
