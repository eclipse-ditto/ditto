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
package org.eclipse.ditto.services.authorization.util.update;

import static java.util.Objects.requireNonNull;

import java.util.Set;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;

/**
 * Listens to events, updates authorization cache.
 */
public final class CacheUpdater extends AbstractActor {

    final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    final ActorRef pubSubMediator;
    final Set<CacheUpdateStrategy> cacheUpdateStrategies;

    /**
     * Creates a cache updater.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param cacheUpdateStrategies cache update strategies.
     * @param instanceIndex index of this service instance.
     */
    public CacheUpdater(final ActorRef pubSubMediator,
            final Set<CacheUpdateStrategy> cacheUpdateStrategies,
            final int instanceIndex) {
        this.pubSubMediator = requireNonNull(pubSubMediator);
        this.cacheUpdateStrategies = requireNonNull(cacheUpdateStrategies);

        final String group = getSelf().path().name() + instanceIndex;
        cacheUpdateStrategies.stream()
                .map(CacheUpdateStrategy::getEventTopic)
                .forEach(topic -> pubSubMediator.tell(subscribe(topic, group), getSelf()));
    }

    @Override
    public Receive createReceive() {
        return handlers().orElse(ReceiveBuilder.create()
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        log.debug("Got SubscribeAck about topic <{}> for group <{}>",
                                subscribeAck.subscribe().topic(),
                                subscribeAck.subscribe().group()))
                .matchAny(message -> {
                    log.warning("Unhandled message <{}>", message);
                    unhandled(message);
                })
                .build());
    }

    /**
     * Return a partial function to handle events this actor subscribes to.
     *
     * @return a partial function.
     */
    protected Receive handlers() {
        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create();
        for (final CacheUpdateStrategy strategy : cacheUpdateStrategies) {
            @SuppressWarnings("unchecked")
            final Class<Object> eventClass = strategy.getEventClass();
            @SuppressWarnings("unchecked")
            final FI.UnitApply<Object> eventHandler = strategy::handleEvent;
            receiveBuilder.match(eventClass, eventHandler);
        }
        return receiveBuilder.build();
    }

    private DistributedPubSubMediator.Subscribe subscribe(final String topic, final String group) {
        return new DistributedPubSubMediator.Subscribe(topic, group, getSelf());
    }

}
