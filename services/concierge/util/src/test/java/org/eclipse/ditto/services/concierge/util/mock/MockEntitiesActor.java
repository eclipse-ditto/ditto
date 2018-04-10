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
package org.eclipse.ditto.services.concierge.util.mock;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithResource;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.javadsl.TestKit;

/**
 * Mock entity shard region actor that delivers one canned response per resource type.
 */
public final class MockEntitiesActor extends AbstractActor {

    public static final NoReply NO_REPLY = new NoReply();

    private final Map<String, Object> replies = new HashMap<>();

    public static Props props() {
        return Props.create(MockEntitiesActor.class, MockEntitiesActor::new);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Set.class, set -> {
                    replies.put(set.resourceType, set.reply);
                    getSender().tell(set, getSelf());
                })
                .match(Signal.class, signal -> getSender().tell(getReply(signal), getSelf()))
                .matchAny(message -> getSender().tell(NO_REPLY, getSelf()))
                .build();
    }

    public void setReply(final TestKit testKit, final WithResource withResource) {
        setReply(testKit, withResource.getResourceType(), withResource);
    }

    public void setReply(final TestKit testKit, final String resourceType, final Object reply) {
        final Set set = new Set(resourceType, reply);
        getSelf().tell(set, testKit.getRef());
        testKit.expectMsg(set);
    }

    private Object getReply(final Signal signal) {
        final Object reply = replies.get(signal.getResourceType());
        return reply != null ? reply : NO_REPLY;
    }

    public static final class Set {

        private final String resourceType;
        private final Object reply;

        public Set(final String resourceType, final Object reply) {
            this.resourceType = resourceType;
            this.reply = reply;
        }
    }

    public static final class NoReply {}
}
