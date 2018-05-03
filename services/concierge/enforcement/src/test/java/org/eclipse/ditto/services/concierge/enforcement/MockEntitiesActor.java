/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.enforcement;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithResource;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * Mock entity shard region actor that delivers one canned response per resource type.
 */
public final class MockEntitiesActor extends AbstractActor {

    public static final NoReply NO_REPLY = new NoReply();

    private final Map<String, Object> replies = new ConcurrentHashMap<>();

    public static Props props() {
        return Props.create(MockEntitiesActor.class, MockEntitiesActor::new);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Signal.class, signal -> getSender().tell(getReply(signal), getSelf()))
                .matchAny(message -> getSender().tell(NO_REPLY, getSelf()))
                .build();
    }

    public MockEntitiesActor setReply(final WithResource withResource) {
        return setReply(withResource.getResourceType(), withResource);
    }

    public MockEntitiesActor setReply(final String resourceType, final Object reply) {
        replies.put(resourceType, reply);
        return this;
    }

    private Object getReply(final Signal signal) {
        return replies.getOrDefault(signal.getType(), replies.getOrDefault(signal.getResourceType(), NO_REPLY));
    }

    public static final class NoReply {}
}
