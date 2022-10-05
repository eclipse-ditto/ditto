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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that acts like a shard region but does not require the actor system to be in a cluster.
 */
final class MockShardRegionActor extends AbstractActor {

    private final Props props;
    private final ShardRegionExtractor extractor;
    private final Map<String, ActorRef> entities = new HashMap<>();

    private MockShardRegionActor(final Props props, final ShardRegionExtractor extractor) {
        this.props = props;
        this.extractor = extractor;
    }

    static Props props(final Props props, final ShardRegionExtractor extractor) {
        return Props.create(MockShardRegionActor.class, props, extractor);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Terminated.class, this::childTerminated)
                .matchAny(this::forwardToChild)
                .build();
    }

    private void forwardToChild(final Object message) {
        final var entityId = extractor.entityId(message);
        final var entity = entities.computeIfAbsent(entityId, k ->
                getContext().watch(getContext().actorOf(props, k)));
        entity.forward(extractor.entityMessage(message), getContext());
    }

    private void childTerminated(final Terminated terminated) {
        final var childKey = entities.entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(terminated.actor()))
                .map(Map.Entry::getKey)
                .findAny();
        childKey.ifPresent(entities::remove);
    }
}
