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
package org.eclipse.ditto.services.base.actors;

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
     * @param self reference of the actor itself.
     * @return the actor behavior.
     */
    public static ShutdownNamespaceBehavior fromId(final String entityId, final ActorRef self) {
        final String namespace = NamespaceCacheWriter.namespaceFromId(entityId).orElse("");
        return new ShutdownNamespaceBehavior(namespace, self);
    }

    /**
     * Initialize by subscribing to {@link ShutdownNamespace}.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @return this object.
     */
    public ShutdownNamespaceBehavior initPubSub(final ActorRef pubSubMediator) {
        final DistributedPubSubMediator.Subscribe subscribe =
                new DistributedPubSubMediator.Subscribe(ShutdownNamespace.TYPE, self);
        pubSubMediator.tell(subscribe, self);
        return this;
    }

    /**
     * @return the class of shutdown messages.
     */
    public Class<ShutdownNamespace> shutdownClass() {
        return ShutdownNamespace.class;
    }

    /**
     * Process {@link ShutdownNamespace} commands and terminate self if the namespace matches.
     *
     * @param shutdownNamespace the command.
     */
    public void shutdown(final ShutdownNamespace shutdownNamespace) {
        if (Objects.equals(namespace, shutdownNamespace.getNamespace())) {
            self.tell(PoisonPill.getInstance(), ActorRef.noSender());
        }
    }

    /**
     * @return the class of subscription acknowledgements.
     */
    public Class<DistributedPubSubMediator.SubscribeAck> subscribeAckClass() {
        return DistributedPubSubMediator.SubscribeAck.class;
    }

    /**
     * Handle subscription acknowledgements by doing nothing.
     *
     * @param ack the acknowledgement message.
     */
    public void subscribeAck(final DistributedPubSubMediator.SubscribeAck ack) {
        // do nothing
    }

    /**
     * Create a new receive builder matching on messages handled by this actor.
     *
     * @return new receive builder.
     */
    public ReceiveBuilder createReceive() {
        return ReceiveBuilder.create()
                .match(shutdownClass(), this::shutdown)
                .match(subscribeAckClass(), this::subscribeAck);
    }
}
