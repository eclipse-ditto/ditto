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

import java.time.Duration;

import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import akka.actor.AbstractActor;
import akka.actor.ActorRefFactory;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Mock actor that live for an instant to deliver information available only to actors.
 */
final class MockActor extends AbstractActor {

    private static final Duration TIMEOUT = Duration.ofSeconds(10L);

    static ThreadSafeDittoLoggingAdapter getThreadSafeDittoLoggingAdapter(final ActorRefFactory factory) {
        return Patterns.ask(factory.actorOf(Props.create(MockActor.class)), Control.GET_LOGGING_ADAPTER, TIMEOUT)
                .thenApply(ThreadSafeDittoLoggingAdapter.class::cast)
                .toCompletableFuture()
                .join();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(Control.GET_LOGGING_ADAPTER, msg -> {
                    getSender().tell(DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this), getSelf());
                    getContext().stop(getSelf());
                })
                .build();
    }

    private enum Control {
        GET_LOGGING_ADAPTER
    }
}
