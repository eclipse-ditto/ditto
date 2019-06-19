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
package org.eclipse.ditto.services.concierge.enforcement;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithResource;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Mock entity shard region actor that delivers one canned response per resource type.
 */
public final class MockEntitiesActor extends AbstractActor {

    public static final NoReply NO_REPLY = new NoReply();

    private final Map<String, Function<Signal, Object>> handlers = new ConcurrentHashMap<>();

    public static Props props() {
        return Props.create(MockEntitiesActor.class);
    }

    final LoggingAdapter log = LogUtil.obtain(this);

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Signal.class, signal -> {
                    log.info("Got signal <{}>", signal);
                    getSender().tell(getReply(signal), getSelf());
                })
                .matchAny(message -> {
                    log.info("Got non-signal <{}>", message);
                    getSender().tell(NO_REPLY, getSelf());
                })
                .build();
    }

    public MockEntitiesActor setReply(final WithResource withResource) {
        return setReply(withResource.getResourceType(), withResource);
    }

    public MockEntitiesActor setReply(final String resourceType, final Object reply) {
        handlers.put(resourceType, signal -> reply);
        return this;
    }

    public MockEntitiesActor setHandler(final String resourceType, final Function<Signal, Object> handler) {
        handlers.put(resourceType, handler);
        return this;
    }

    private Object getReply(final Signal signal) {
        return handlers.getOrDefault(signal.getType(),
                handlers.getOrDefault(signal.getResourceType(),
                        sameSignal -> NO_REPLY))
                .apply(signal);
    }

    public static final class NoReply {}
}
