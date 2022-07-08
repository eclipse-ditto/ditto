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

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.internal.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;

import com.typesafe.config.Config;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

public final class SearchForwardingClientActorPropsFactory implements ClientActorPropsFactory {

    /**
     * @param actorSystem the actor system in which to load the extension.
     */
    public SearchForwardingClientActorPropsFactory(final ActorSystem actorSystem, final Config config) {
    }

    @Override
    public Props getActorPropsForType(final Connection connection, final ActorRef commandForwarderActor,
            final ActorRef connectionActor,
            final ActorSystem actorSystem, final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {

        return SearchForwardingClientActor.props(connectionActor);
    }

    /**
     * Mocks a {@link org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceActor} and provides abstraction for a real connection.
     */
    public static class SearchForwardingClientActor extends AbstractActorWithStash {

        private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

        private final ActorRef mediator = DistributedPubSub.get(getContext().getSystem()).mediator();
        @Nullable private ActorRef delegate;
        @Nullable private ActorRef gossip;
        private ActorRef connectionActor;

        private SearchForwardingClientActor(final ActorRef connectionActor) {
            this.connectionActor = connectionActor;
        }

        public static Props props(final ActorRef connectionActor) {
            return Props.create(SearchForwardingClientActor.class, connectionActor);
        }

        @Override
        public void preStart() {
            log.info("Mock client actor started.");
            connectionActor.tell(getSelf(), getSelf());
            if (gossip != null) {
                gossip.tell(getSelf(), getSelf());
            }
            subscribeForSnapshotPubSubTopic(mediator);
        }

        private void subscribeForSnapshotPubSubTopic(final ActorRef pubSubMediator) {
            final var self = getSelf();
            final var subscriptionMessage =
                    DistPubSubAccess.subscribe("mockClientActor:change", self);
            pubSubMediator.tell(subscriptionMessage, self);
        }

        @Override
        public void postStop() {
            log.info("Mock client actor was stopped.");
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                            mediator.tell(DistPubSubAccess.publish("mockClientActor:subscribed",
                                            new MockClientActorPropsFactory.MockClientActor.Subscribed()),
                                    getSelf()))
                    .match(MockClientActorPropsFactory.MockClientActor.ChangeActorRef.class, s -> {
                        delegate =
                                s.delegate != null ? getContext().getSystem().provider().resolveActorRef(s.delegate) :
                                        null;
                        gossip =
                                s.gossip != null ? getContext().getSystem().provider().resolveActorRef(s.gossip) : null;
                        if (gossip != null) {
                            gossip.tell(getSelf(), getSelf());
                        }
                        getSender().tell(new MockClientActorPropsFactory.MockClientActor.ActorRefChanged(), getSelf());
                        log.info("Switching state.");
                        getContext().become(initializedBehavior(), false);
                        unstashAll();
                    })
                    .matchAny(any -> stash())
                    .build();
        }

        private Receive initializedBehavior() {
            return ReceiveBuilder.create()
                    .match(WithDittoHeaders.class, message -> delegate
                            .tell(WithSender.of(message, getSelf()), getSender()))
                    .match(ActorRef.class, actorRef ->
                            gossip.forward(actorRef, getContext()))
                    .build();
        }

    }
}
