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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory.getDiagnosticLoggingAdapter;

import org.eclipse.ditto.services.utils.aggregator.ThingsAggregatorProxyActor;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Actor which delegates {@link Command}s received in connectivity service to the appropriate receivers in the cluster.
 */
public class ConnectivityProxyActor extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "connectivityProxyActor";

    private final DittoDiagnosticLoggingAdapter log = getDiagnosticLoggingAdapter(this);

    private final ActorRef conciergeForwarder;
    private final ActorRef aggregatorProxyActor;

    private ConnectivityProxyActor(final ActorRef conciergeForwarder) {
        this.conciergeForwarder = conciergeForwarder;
        this.aggregatorProxyActor = getContext().actorOf(ThingsAggregatorProxyActor.props(conciergeForwarder),
                ThingsAggregatorProxyActor.ACTOR_NAME);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param conciergeForwarder the concierge forwarder
     * @return the Props object
     */
    public static Props props(final ActorRef conciergeForwarder) {
        return Props.create(ConnectivityProxyActor.class, conciergeForwarder);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                // handle RetrieveThings in aggregator actor
                .match(RetrieveThings.class, rt -> {
                    log.withCorrelationId(rt).debug("Passing RetrieveThings to aggregator.");
                    aggregatorProxyActor.forward(rt, getContext());
                })
                // forward all other signals to concierge
                .match(Signal.class, signal -> {
                    // This message is important to check if a command is accepted for a specific connection, as this happens
                    // quite a lot this is going to the debug level. Use best with a connection-id filter.
                    log.withCorrelationId(signal).debug("Passing '{}' signal to conciergeForwarder.", signal.getType());
                    conciergeForwarder.forward(signal, getContext());
                })
                // drop and log
                .matchAny(m -> log.info("unexpected message of type {}", m.getClass().getName()))
                .build();
    }
}
