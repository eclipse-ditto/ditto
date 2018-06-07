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

import java.util.function.Function;

import org.eclipse.ditto.services.models.concierge.ConciergeWrapper;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which acts as a client to the concierge service. It forwards messages either to the concierge's appropriate
 * enforcer shard region (in case of a command referring to a single entity) or to the concierge's dispatcher actor (in
 * case of commands not referring to a single entity such as search commands.
 */
public class ConciergeForwarderActor extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "conciergeForwarder";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final ActorRef conciergeShardRegion;
    private final Function<Signal<?>, Signal<?>> signalTransformer;

    private ConciergeForwarderActor(final ActorRef pubSubMediator, final ActorRef conciergeShardRegion,
            final Function<Signal<?>, Signal<?>> signalTransformer) {
        this.pubSubMediator = pubSubMediator;
        this.conciergeShardRegion = conciergeShardRegion;
        this.signalTransformer = signalTransformer;
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param pubSubMediator the PubSub mediator Actor.
     * @param conciergeShardRegion the ActorRef of the concierge shard region.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ActorRef conciergeShardRegion) {
        return props(pubSubMediator, conciergeShardRegion, Function.identity());
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param pubSubMediator the PubSub mediator Actor.
     * @param conciergeShardRegion the ActorRef of the concierge shard region.
     * @param signalTransformer a function which transforms signals before forwarding them to the {@code
     * conciergeShardRegion}
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ActorRef conciergeShardRegion,
            final Function<Signal<?>, Signal<?>> signalTransformer) {

        return Props.create(ConciergeForwarderActor.class, new Creator<ConciergeForwarderActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ConciergeForwarderActor create() {
                return new ConciergeForwarderActor(pubSubMediator, conciergeShardRegion, signalTransformer);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Signal.class, signal -> forward(signal, getSender()))
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        log.debug("Successfully subscribed to distributed pub/sub on topic '{}'",
                                subscribeAck.subscribe().topic())
                )
                .matchAny(m -> log.warning("Got unknown message: {}", m))
                .build();
    }

    /**
     * Forwards the passed {@code signal} based on whether it has an entity ID or not to the {@code pubSubMediator} or
     * the {@code conciergeShardRegion}.
     *
     * @param signal the Signal to forward
     * @param sender the ActorRef to use as sender
     */
    private void forward(final Signal<?> signal, final ActorRef sender) {

        final Signal<?> transformedSignal = signalTransformer.apply(signal);

        if (transformedSignal.getId().isEmpty()) {
            log.debug("Signal does not contain ID, forwarding to concierge-dispatcherActor: <{}>.", transformedSignal);
            final DistributedPubSubMediator.Send msg = wrapForPubSub(transformedSignal);
            log.debug("Sending message to concierge-dispatcherActor: <{}>.", msg);
            pubSubMediator.tell(msg, sender);
        } else {
            log.debug("Signal has ID <{}>, forwarding to concierge-shard-region: <{}>.",
                    transformedSignal.getId(), transformedSignal);
            final ShardedMessageEnvelope msg = ConciergeWrapper.wrapForEnforcer(transformedSignal);
            log.debug("Sending message to concierge-shard-region: <{}>.", msg);
            conciergeShardRegion.tell(msg, sender);
        }
    }

    private static DistributedPubSubMediator.Send wrapForPubSub(final Signal<?> signal) {
        return new DistributedPubSubMediator.Send(DISPATCHER_ACTOR_PATH, signal);
    }

}
