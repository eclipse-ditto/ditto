/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.base.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import org.eclipse.ditto.signals.commands.devops.namespace.ShutdownNamespace;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor behavior that shuts itself down on command.
 */
public final class ShutdownNamespaceBehavior {

    private final String namespace;
    private final ActorRef self;

    private ShutdownNamespaceBehavior(final String namespace, final ActorRef self) {
        this.namespace = namespace;
        this.self = self;
    }

    /**
     * Create the actor behavior from its entity ID and reference.
     *
     * @param entityId entity ID containing a namespace to react to.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param self reference of the actor itself.
     * @return the actor behavior.
     */
    public static ShutdownNamespaceBehavior fromId(final String entityId,
            final ActorRef pubSubMediator, final ActorRef self) {
        checkNotNull(entityId, "Entity ID");
        checkNotNull(pubSubMediator, "PubSub Mediator");
        checkNotNull(self, "Self");

        final String namespace = NamespaceReader.getInstance().fromEntityId(entityId).orElse("");
        final ShutdownNamespaceBehavior shutdownNamespaceBehavior = new ShutdownNamespaceBehavior(namespace, self);
        shutdownNamespaceBehavior.subscribePubSub(pubSubMediator);
        return shutdownNamespaceBehavior;
    }

    /**
     * Create a new receive builder matching on messages handled by this actor.
     *
     * @return new receive builder.
     */
    public ReceiveBuilder createReceive() {
        return ReceiveBuilder.create()
                .match(ShutdownNamespace.class, this::shutdown)
                .match(DistributedPubSubMediator.SubscribeAck.class, this::subscribeAck);
    }

    private void subscribePubSub(final ActorRef pubSubMediator) {
        final DistributedPubSubMediator.Subscribe subscribe =
                new DistributedPubSubMediator.Subscribe(ShutdownNamespace.TYPE, self);
        pubSubMediator.tell(subscribe, self);
    }

    private void subscribeAck(final DistributedPubSubMediator.SubscribeAck ack) {
        // do nothing
    }

    private void shutdown(final ShutdownNamespace shutdownNamespace) {
        if (Objects.equals(namespace, shutdownNamespace.getNamespace())) {
            self.tell(PoisonPill.getInstance(), ActorRef.noSender());
        }
    }

}
