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
package org.eclipse.ditto.edge.api.dispatching;

import java.util.function.Function;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which acts as a client to the concierge service. It forwards messages either to the concierge's appropriate
 * enforcer (in case of a command referring to a single entity) or to the concierge's dispatcher actor (in
 * case of commands not referring to a single entity such as search commands).
 * TODO TJ candidate for removal or for renaming
 */
public class ConciergeForwarderActor extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "conciergeForwarder";

    /**
     * TODO TJ where to? still needed?
     */
    private static final String DISPATCHER_ACTOR_PATH = "/user/conciergeRoot/dispatcherActor";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef pubSubMediator;
    private final ShardRegions shardRegions;
    private final Function<Signal<?>, Signal<?>> signalTransformer;

    @SuppressWarnings("unused")
    private ConciergeForwarderActor(final ActorRef pubSubMediator, final ShardRegions shardRegions) {

        this.pubSubMediator = pubSubMediator;
        this.shardRegions = shardRegions;
        signalTransformer = SignalTransformer.get(getContext().getSystem());
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param pubSubMediator the PubSub mediator Actor.
     * @param shardRegions shard regions to use in order to dispatch different entity Signals to.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ShardRegions shardRegions) {
        return Props.create(ConciergeForwarderActor.class, pubSubMediator, shardRegions);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(MessageCommand.class, this::forwardToThings)
                .match(ThingCommand.class, this::forwardToThings)
                .match(PolicyCommand.class, this::forwardToPolicies)
                .match(ConnectivityCommand.class, this::forwardToConnectivity)
                .match(Signal.class, this::forwardSignalToPubSub)
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        log.debug("Successfully subscribed to distributed pub/sub on topic '{}'",
                                subscribeAck.subscribe().topic())
                )
                .matchAny(m -> log.warning("Got unknown message: {}", m))
                .build();
    }

    private void forwardToThings(final MessageCommand<?, ?> messageCommand) {
        final MessageCommand<?, ?> transformedMessageCommand =
                (MessageCommand<?, ?>) signalTransformer.apply(messageCommand);
        log.withCorrelationId(transformedMessageCommand)
                .info("Forwarding message command with ID <{}> and type <{}> to 'things' shard region",
                        transformedMessageCommand.getEntityId(), transformedMessageCommand.getType());
        shardRegions.things()
                .forward(transformedMessageCommand, getContext());
    }

    private void forwardToThings(final ThingCommand<?> thingCommand) {
        final ThingCommand<?> transformedThingCommand = (ThingCommand<?>) signalTransformer.apply(thingCommand);
        log.withCorrelationId(transformedThingCommand)
                .info("Forwarding thing command with ID <{}> and type <{}> to 'things' shard region",
                        transformedThingCommand.getEntityId(), transformedThingCommand.getType());
        shardRegions.things()
                .forward(transformedThingCommand, getContext());
    }

    private void forwardToPolicies(final PolicyCommand<?> policyCommand) {
        final PolicyCommand<?> transformedPolicyCommand = (PolicyCommand<?>) signalTransformer.apply(policyCommand);
        log.withCorrelationId(transformedPolicyCommand)
                .info("Forwarding policy command with ID <{}> and type <{}> to 'policies' shard region",
                        transformedPolicyCommand.getEntityId(), transformedPolicyCommand.getType());
        shardRegions.policies()
                .forward(transformedPolicyCommand, getContext());
    }

    private void forwardToConnectivity(final ConnectivityCommand<?> connectivityCommand) {
        if (connectivityCommand instanceof WithEntityId withEntityId) {
            final ConnectivityCommand<?> transformedConnectivityCommand =
                    (ConnectivityCommand<?>) signalTransformer.apply(connectivityCommand);
            log.withCorrelationId(transformedConnectivityCommand)
                    .info("Forwarding connectivity command with ID <{}> and type <{}> to 'connections' " +
                            "shard region", withEntityId.getEntityId(), transformedConnectivityCommand.getType());
            shardRegions.connections()
                    .forward(transformedConnectivityCommand, getContext());
        } else {
            log.withCorrelationId(connectivityCommand)
                    .error("Could not forward ConnectivityCommand not implementing WithEntityId to 'connections' " +
                            "shard region");
        }
    }

    private void forwardSignalToPubSub(final Signal<?> signal) {

        final Signal<?> transformedSignal = signalTransformer.apply(signal);
        final String signalType = transformedSignal.getType();
        final DittoDiagnosticLoggingAdapter l = log.withCorrelationId(transformedSignal);
        l.info("Sending signal without ID and type <{}> via pubSub to concierge-dispatcherActor",
                signalType);
        l.debug("Sending signal without ID and type <{}> via pubSub to concierge-dispatcherActor: <{}>",
                signalType, transformedSignal);
        final DistributedPubSubMediator.Send msg = wrapForPubSub(transformedSignal);
        l.debug("Forwarding message to dispatcherActor: <{}>.", msg);
        pubSubMediator.forward(msg, getContext());
    }

    private static DistributedPubSubMediator.Send wrapForPubSub(final Signal<?> signal) {
        return DistPubSubAccess.send(DISPATCHER_ACTOR_PATH, signal);
    }

}
