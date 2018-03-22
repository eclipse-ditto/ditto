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

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Listens to events, updates authorization cache.
 */
public abstract class AbstractCacheUpdater extends AbstractActor {

    final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    final ActorRef pubSubMediator;
    final AuthorizationCaches caches;

    /**
     * Creates a cache updater.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param caches authorization caches.
     * @param instanceIndex index of this service instance.
     */
    protected AbstractCacheUpdater(final ActorRef pubSubMediator,
            final AuthorizationCaches caches,
            final int instanceIndex) {
        this.pubSubMediator = pubSubMediator;
        this.caches = caches;

        final String group = getSelf().path().name() + instanceIndex;
        subscriptions().forEach(topic -> pubSubMediator.tell(subscribe(topic, group), getSelf()));
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
     * Return a collection of event topics this actor subscribes to.
     *
     * @return collection of topics.
     */
    protected Collection<String> subscriptions() {
        return Arrays.asList(ThingEvent.TYPE_PREFIX, PolicyEvent.TYPE_PREFIX);
    }

    /**
     * Return a partial function to handle events this actor subscribes to.
     *
     * @return a partial function.
     */
    protected Receive handlers() {
        return ReceiveBuilder.create()
                .match(ThingEvent.class, this::handleThingEvent)
                .match(PolicyEvent.class, this::handlePolicyEvent)
                .build();
    }

    private void handleThingEvent(final ThingEvent thingEvent) {
        // TODO CR-5397: be less wasteful.
        final EntityId key = EntityId.of(ThingCommand.RESOURCE_TYPE, thingEvent.getThingId());
        caches.invalidateAll(key);
    }

    private void handlePolicyEvent(final PolicyEvent policyEvent) {
        // TODO CR-5397: be less wasteful.
        final EntityId key = EntityId.of(PolicyCommand.RESOURCE_TYPE, policyEvent.getPolicyId());
        caches.invalidateAll(key);
    }

    private DistributedPubSubMediator.Subscribe subscribe(final String topic, final String group) {
        return new DistributedPubSubMediator.Subscribe(topic, group, getSelf());
    }

}
