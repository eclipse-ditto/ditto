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
package org.eclipse.ditto.services.models.concierge.actors;

import static org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants.DISPATCHER_ACTOR_PATH;

import org.eclipse.ditto.services.models.concierge.ConciergeWrapper;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * TODO Javadoc
 */
public class ConciergeForwarderActor extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "conciergeForwarder";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final ActorRef enforcerShardRegion;

    private ConciergeForwarderActor(final ActorRef pubSubMediator, final ActorRef enforcerShardRegion) {
        this.pubSubMediator = pubSubMediator;
        this.enforcerShardRegion = enforcerShardRegion;
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param pubSubMediator the PubSub mediator Actor.
     * @param enforcerShardRegion the ActorRef of the enforcerShardRegion.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ActorRef enforcerShardRegion) {

        return Props.create(ConciergeForwarderActor.class,
                () -> new ConciergeForwarderActor(pubSubMediator, enforcerShardRegion));
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Signal.class, signal -> forward(signal, sender()))
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        log.debug("Successfully subscribed to distributed pub/sub on topic '{}'",
                                subscribeAck.subscribe().topic())
                )
                .matchAny(m -> log.warning("Got unknown message: {}", m))
                .build();
    }

    /**
     * Forwards the passed {@code signal} based on whether it has an entity ID or not to the {@code pubSubMediator}
     * or the {@code enforcerShardRegion}.
     *
     * @param signal the Signal to forward
     * @param sender the ActorRef to use as sender
     */
    private void forward(final Signal<?> signal, final ActorRef sender) {
        if (signal.getId().isEmpty()) {
            log.debug("Signal does not contain ID, forwarding to concierge-dispatcherActor: <{}>.", signal);
            final DistributedPubSubMediator.Send msg = wrapForPubSub(signal);
            log.debug("Sending message to concierge-dispatcherActor: <{}>.", msg);
            pubSubMediator.tell(msg, sender);
        } else {
            log.debug("Signal has ID <{}>, forwarding to concierge-shard-region: <{}>.",
                    signal.getId(), signal);
            final ShardedMessageEnvelope msg = ConciergeWrapper.wrapForEnforcer(signal);
            log.debug("Sending message to concierge-shard-region: <{}>.", msg);
            enforcerShardRegion.tell(msg, sender);
        }
    }

    private static DistributedPubSubMediator.Send wrapForPubSub(final Signal<?> signal) {
        return new DistributedPubSubMediator.Send(DISPATCHER_ACTOR_PATH, signal);
    }

}
