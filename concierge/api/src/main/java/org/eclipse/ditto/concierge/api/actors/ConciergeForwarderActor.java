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
package org.eclipse.ditto.concierge.api.actors;

import java.util.Optional;
import java.util.function.Function;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.concierge.api.ConciergeMessagingConstants;
import org.eclipse.ditto.concierge.api.ConciergeWrapper;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.base.model.signals.Signal;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which acts as a client to the concierge service. It forwards messages either to the concierge's appropriate
 * enforcer (in case of a command referring to a single entity) or to the concierge's dispatcher actor (in
 * case of commands not referring to a single entity such as search commands).
 */
public class ConciergeForwarderActor extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "conciergeForwarder";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef pubSubMediator;
    private final ActorRef conciergeEnforcer;
    private final Function<Signal<?>, Signal<?>> signalTransformer;

    @SuppressWarnings("unused")
    private ConciergeForwarderActor(final ActorRef pubSubMediator, final ActorRef conciergeEnforcer,
            final Function<Signal<?>, Signal<?>> signalTransformer) {
        this.pubSubMediator = pubSubMediator;
        this.conciergeEnforcer = conciergeEnforcer;
        this.signalTransformer = signalTransformer;
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param pubSubMediator the PubSub mediator Actor.
     * @param conciergeEnforcer the ActorRef of the concierge EnforcerActor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ActorRef conciergeEnforcer) {
        return props(pubSubMediator, conciergeEnforcer, Function.identity());
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param pubSubMediator the PubSub mediator Actor.
     * @param conciergeEnforcer the ActorRef of the concierge EnforcerActor.
     * @param signalTransformer a function which transforms signals before forwarding them to the {@code
     * conciergeEnforcer}
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ActorRef conciergeEnforcer,
            final Function<Signal<?>, Signal<?>> signalTransformer) {

        return Props.create(ConciergeForwarderActor.class, pubSubMediator, conciergeEnforcer, signalTransformer);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Signal.class, signal -> forward(signal, getContext()))
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
     * @param ctx the ActorRef to use as sender
     */
    private void forward(final Signal<?> signal, final ActorContext ctx) {

        final Signal<?> transformedSignal = signalTransformer.apply(signal);

        final String signalType = transformedSignal.getType();
        final DittoDiagnosticLoggingAdapter l = log.withCorrelationId(signal);
        toSignalWithEntityId(transformedSignal).ifPresentOrElse(
                transformedSignalWithEntityId -> {
                    l.info("Forwarding signal with ID <{}> and type <{}> to concierge enforcer",
                            transformedSignalWithEntityId.getEntityId(), signalType);
                    final Object msg = ConciergeWrapper.wrapForEnforcerRouter(transformedSignalWithEntityId);
                    l.debug("Forwarding message to concierge enforcer: <{}>", msg);
                    conciergeEnforcer.forward(msg, ctx);
                },
                () -> {
                    l.info("Sending signal without ID and type <{}> via pubSub to concierge-dispatcherActor",
                            signalType);
                    l.debug("Sending signal without ID and type <{}> via pubSub to concierge-dispatcherActor: <{}>",
                            signalType, transformedSignal);
                    final DistributedPubSubMediator.Send msg = wrapForPubSub(transformedSignal);
                    l.debug("Forwarding message to concierge-dispatcherActor via pub/sub: <{}>.", msg);
                    pubSubMediator.forward(msg, ctx);
                }
        );
    }

    private static <C extends Signal<?> & WithEntityId> Optional<C> toSignalWithEntityId(Signal<?> signal) {
        if (signal instanceof WithEntityId) {
            return Optional.of((C) signal);
        } else {
            return Optional.empty();
        }
    }

    private static DistributedPubSubMediator.Send wrapForPubSub(final Signal<?> signal) {
        return DistPubSubAccess.send(ConciergeMessagingConstants.DISPATCHER_ACTOR_PATH, signal);
    }

}
