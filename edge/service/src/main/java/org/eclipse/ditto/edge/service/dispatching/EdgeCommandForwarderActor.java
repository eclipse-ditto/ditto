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
package org.eclipse.ditto.edge.service.dispatching;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.internal.utils.aggregator.ThingsAggregatorProxyActor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.thingsearch.api.ThingsSearchConstants;
import org.eclipse.ditto.thingsearch.api.commands.sudo.ThingSearchSudoCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which acts as a client used at the Ditto edges (gateway and connectivity) in order to forward messages
 * directly to the shard regions of the services the commands are targeted to.
 * For "thing search" commands, it sends them via pub/sub to the SearchActor.
 */
public class EdgeCommandForwarderActor extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "edgeCommandForwarder";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef pubSubMediator;
    private final ShardRegions shardRegions;
    private final SignalTransformer signalTransformer;
    private final AskWithRetryCommandForwarder askWithRetryCommandForwarder;
    private final ActorRef aggregatorProxyActor;

    @SuppressWarnings("unused")
    private EdgeCommandForwarderActor(final ActorRef pubSubMediator, final ShardRegions shardRegions,
            final SignalTransformer signalTransformer) {

        this.pubSubMediator = pubSubMediator;
        this.shardRegions = shardRegions;
        this.signalTransformer = signalTransformer;
        final AskWithRetryConfig askWithRetryConfig = DefaultAskWithRetryConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()),
                "ask-with-retry");
        final ActorSystem actorSystem = getContext().getSystem();
        askWithRetryCommandForwarder = AskWithRetryCommandForwarder.get(actorSystem);
        aggregatorProxyActor = getContext().actorOf(ThingsAggregatorProxyActor.props(pubSubMediator),
                ThingsAggregatorProxyActor.ACTOR_NAME);
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param pubSubMediator the PubSub mediator Actor.
     * @param shardRegions shard regions to use in order to dispatch different entity Signals to.
     * @param signalTransformer Used to transform signals before forwarding them to the responsible service.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ShardRegions shardRegions,
            final SignalTransformer signalTransformer) {

        return Props.create(EdgeCommandForwarderActor.class, pubSubMediator, shardRegions, signalTransformer);
    }

    /**
     * Determines whether the passed {@code command} is idempotent or not.
     *
     * @param command the command to check.
     * @return whether the command is idempotent or not.
     */
    public static boolean isIdempotent(final Command<?> command) {
        return switch (command.getCategory()) {
            case QUERY, MERGE, MODIFY, DELETE -> true;
            default -> false;
        };
    }

    @Override
    public Receive createReceive() {
        final Receive receiveExtension = EdgeCommandForwarderExtension.get(context().system())
                .getReceiveExtension(getContext());

        final Receive forwardingReceive = ReceiveBuilder.create()
                .match(MessageCommand.class, this::forwardToThings)
                .match(MessageCommandResponse.class, this::forwardToThings)
                .match(ThingCommand.class, this::forwardToThings)
                .match(ThingCommandResponse.class, CommandResponse::isLiveCommandResponse, this::forwardToThings)
                .match(ThingEvent.class, Event::isLiveEvent, this::forwardToThings)
                .match(RetrieveThings.class, this::forwardToThingsAggregatorProxy)
                .match(SudoRetrieveThings.class, this::forwardToThingsAggregatorProxy)
                .match(PolicyCommand.class, this::forwardToPolicies)
                .match(ConnectivityCommand.class, this::forwardToConnectivity)
                .match(ThingSearchCommand.class, this::forwardToThingSearch)
                .match(ThingSearchSudoCommand.class, this::forwardToThingSearch)
                .match(Signal.class, this::handleUnknownSignal)
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        log.debug("Successfully subscribed to distributed pub/sub on topic '{}'",
                                subscribeAck.subscribe().topic())
                )
                .matchAny(m -> log.warning("Got unknown message: {}", m))
                .build();

        return receiveExtension.orElse(forwardingReceive);
    }

    private void forwardToThings(final Signal<?> thingSignal) {
        final ActorRef sender = getSender();
        signalTransformer.apply(thingSignal)
                .thenAccept(transformed -> {
                    log.withCorrelationId(transformed)
                            .info("Forwarding thing signal with ID <{}> and type <{}> to 'things' shard region",
                                    transformed instanceof WithEntityId withEntityId ? withEntityId.getEntityId() :
                                            null,
                                    transformed.getType());

                    if (!Signal.isChannelLive(transformed) &&
                            !Signal.isChannelSmart(transformed) &&
                            transformed instanceof Command<?> command &&
                            isIdempotent(command)) {
                        askWithRetryCommandForwarder.forwardCommand(command,
                                shardRegions.things(),
                                sender);
                    } else {
                        shardRegions.things().tell(transformed, sender);
                    }
                });
    }

    private void forwardToThingsAggregatorProxy(final Command<?> command) {
        final ActorRef sender = getSender();
        signalTransformer.apply(command)
                .thenAccept(transformed -> aggregatorProxyActor.tell(transformed, sender));
    }

    private void forwardToPolicies(final PolicyCommand<?> policyCommand) {
        final ActorRef sender = getSender();
        signalTransformer.apply(policyCommand)
                .thenAccept(transformed -> {
                    final PolicyCommand<?> transformedPolicyCommand = (PolicyCommand<?>) transformed;
                    log.withCorrelationId(transformedPolicyCommand)
                            .info("Forwarding policy command with ID <{}> and type <{}> to 'policies' shard region",
                                    transformedPolicyCommand.getEntityId(), transformedPolicyCommand.getType());

                    if (isIdempotent(transformedPolicyCommand)) {
                        askWithRetryCommandForwarder.forwardCommand(transformedPolicyCommand,
                                shardRegions.policies(),
                                sender);
                    } else {
                        shardRegions.policies().tell(transformedPolicyCommand, sender);
                    }
                });

    }

    private void forwardToConnectivity(final ConnectivityCommand<?> connectivityCommand) {
        if (connectivityCommand instanceof WithEntityId withEntityId) {
            final ActorRef sender = getSender();
            signalTransformer.apply(connectivityCommand)
                    .thenAccept(transformed -> {
                        final ConnectivityCommand<?> transformedConnectivityCommand =
                                (ConnectivityCommand<?>) transformed;
                        log.withCorrelationId(transformedConnectivityCommand)
                                .info("Forwarding connectivity command with ID <{}> and type <{}> to 'connections' " +
                                                "shard region", withEntityId.getEntityId(),
                                        transformedConnectivityCommand.getType());

                        // don't retry connectivity commands
                        shardRegions.connections().tell(transformedConnectivityCommand, sender);
                    });
        } else {
            log.withCorrelationId(connectivityCommand)
                    .error("Could not forward ConnectivityCommand not implementing WithEntityId to 'connections' " +
                            "shard region");
        }
    }

    private void forwardToThingSearch(final Command<?> command) {
        // don't use "ask with retry" as the search could take some time and we don't want to stress the search
        // by retrying a query several times if it took long
        pubSubMediator.tell(
                DistPubSubAccess.send(ThingsSearchConstants.SEARCH_ACTOR_PATH, command),
                getSender());
    }

    private void handleUnknownSignal(final Signal<?> signal) {
        signalTransformer.apply(signal)
                .thenAccept(transformedSignal -> {
                    final String signalType = transformedSignal.getType();
                    final DittoDiagnosticLoggingAdapter l = log.withCorrelationId(transformedSignal);
                    l.error("Received signal <{}> which is not known how to be handled: {}",
                            signalType, transformedSignal);
                });
    }

}
