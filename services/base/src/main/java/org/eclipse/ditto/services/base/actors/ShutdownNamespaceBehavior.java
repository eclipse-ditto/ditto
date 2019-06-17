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
package org.eclipse.ditto.services.base.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import org.eclipse.ditto.model.namespaces.NamespaceReader;
import org.eclipse.ditto.signals.commands.common.Shutdown;
import org.eclipse.ditto.signals.commands.common.ShutdownReason;
import org.eclipse.ditto.signals.commands.common.ShutdownReasonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor behavior that shuts itself down on command.
 */
public final class ShutdownNamespaceBehavior {

    private static final Logger LOG = LoggerFactory.getLogger(ShutdownNamespaceBehavior.class);

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
    public static ShutdownNamespaceBehavior fromId(final String entityId, final ActorRef pubSubMediator,
            final ActorRef self) {

        checkNotNull(entityId, "Entity ID");
        checkNotNull(pubSubMediator, "Pub-Sub-Mediator");
        checkNotNull(self, "Self");

        final String namespace = NamespaceReader.fromEntityId(entityId).orElse("");
        final ShutdownNamespaceBehavior shutdownNamespaceBehavior = new ShutdownNamespaceBehavior(namespace, self);
        shutdownNamespaceBehavior.subscribePubSub(pubSubMediator);
        return shutdownNamespaceBehavior;
    }

    private void subscribePubSub(final ActorRef pubSubMediator) {
        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(Shutdown.TYPE, self), self);
    }

    /**
     * Create a new receive builder matching on messages handled by this actor.
     *
     * @return new receive builder.
     */
    public ReceiveBuilder createReceive() {
        return ReceiveBuilder.create()
                .match(Shutdown.class, this::shutdown)
                .match(DistributedPubSubMediator.SubscribeAck.class, this::subscribeAck);
    }

    private void shutdown(final Shutdown shutdown) {
        final ShutdownReason shutdownReason = shutdown.getReason();
        if (ShutdownReasonType.Known.PURGE_NAMESPACE.equals(shutdownReason.getType()) &&
                Objects.equals(namespace, shutdownReason.getDetailsOrThrow())) {
            LOG.info("Shutting down <{}> due to <{}>.", self, shutdown);
            self.tell(PoisonPill.getInstance(), ActorRef.noSender());
        }
    }

    private void subscribeAck(final DistributedPubSubMediator.SubscribeAck ack) {
        // do nothing
    }
}
