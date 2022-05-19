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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.internal.utils.aggregator.DefaultThingsAggregatorConfig;
import org.eclipse.ditto.internal.utils.aggregator.ThingsAggregatorActor;
import org.eclipse.ditto.internal.utils.aggregator.ThingsAggregatorConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.thingsearch.api.ThingsSearchConstants;
import org.eclipse.ditto.thingsearch.api.commands.sudo.ThingSearchSudoCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor which acts as a client used at the Ditto edges (gateway and connectivity) in order to forward messages
 * directly to the shard regions of the services the commands are targeted to.
 * For "thing search" commands, it utilizes the
 */
public class EdgeCommandForwarderActor extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "edgeCommandForwarder";

    private static final Map<String, ThreadSafeDittoLogger> NAMESPACE_INSPECTION_LOGGERS = new HashMap<>();

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef pubSubMediator;
    private final ShardRegions shardRegions;
    private final Function<Signal<?>, Signal<?>> signalTransformer;
    private final DefaultEnforcementConfig enforcementConfig;
    private final ActorRef thingsAggregatorActor;

    @SuppressWarnings("unused")
    private EdgeCommandForwarderActor(final ActorRef pubSubMediator, final ShardRegions shardRegions) {

        this.pubSubMediator = pubSubMediator;
        this.shardRegions = shardRegions;
        signalTransformer = SignalTransformer.get(getContext().getSystem());

        final DefaultScopedConfig dittoScopedConfig =
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config());

        enforcementConfig = DefaultEnforcementConfig.of(dittoScopedConfig);

        enforcementConfig.getSpecialLoggingInspectedNamespaces()
                .forEach(loggedNamespace -> NAMESPACE_INSPECTION_LOGGERS.put(
                        loggedNamespace,
                        DittoLoggerFactory.getThreadSafeLogger(EdgeCommandForwarderActor.class.getName() +
                                ".namespace." + loggedNamespace)));

        final ThingsAggregatorConfig thingsAggregatorConfig = DefaultThingsAggregatorConfig.of(dittoScopedConfig);

        // TODO TJ move starting the ThingsAggregatorActor to search
        //  goal:
        //  * edge receives QueryThingsCommand
        //  * edge command forwarder forwards the search command to search service
        //  * search service performs the search, calculates a list of matching thingIds
        //  * search service contains the ThingsAggregatorActor - which splits up the "RetrieveThings" with the current page size to many single RetrieveThing commands
        //  * ThingsAggregatorActor in search sends e.g. 50 RetrieveThing commands directly to things shard region - the "sender" is preserved as this command forwarder or more precisely the ThingAggregatorProxy actor
        //  * the ThingsAggregatorProxy actor at the edge gets a SourceRef of RetrieveThingResponse commands and creates a single result for the search response from that after aggregation and sorting

        // some optimizations to apply:
        // * if only the "thingId" is wanted in the "fields", directly answer from search back to here
        final Props props = ThingsAggregatorActor.props(getSelf(), thingsAggregatorConfig);
        thingsAggregatorActor = getContext().actorOf(props, ThingsAggregatorActor.ACTOR_NAME);
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param pubSubMediator the PubSub mediator Actor.
     * @param shardRegions shard regions to use in order to dispatch different entity Signals to.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ShardRegions shardRegions) {
        return Props.create(EdgeCommandForwarderActor.class, pubSubMediator, shardRegions);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(MessageCommand.class, this::forwardToThings)
                .match(ThingCommand.class, this::forwardToThings)
                .match(RetrieveThings.class, this::forwardToThingsAggregator)
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

    private void forwardToThingsAggregator(final RetrieveThings retrieveThings) {
        thingsAggregatorActor.forward(retrieveThings, getContext());
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

    private void forwardToThingSearch(final ThingSearchCommand<?> thingSearchCommand) {
        final Set<String> namespaces = thingSearchCommand.getNamespaces().orElseGet(Set::of);
        NAMESPACE_INSPECTION_LOGGERS.entrySet().stream()
                .filter(entry -> namespaces.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .forEach(l -> {
                    if (thingSearchCommand instanceof ThingSearchQueryCommand<?> thingSearchQueryCommand) {
                        final String filter = thingSearchQueryCommand.getFilter().orElse(null);
                        l.withCorrelationId(thingSearchCommand).info(
                                "Forwarding search query command type <{}> with filter <{}> and " +
                                        "fields <{}>",
                                thingSearchCommand.getType(),
                                filter,
                                thingSearchCommand.getSelectedFields().orElse(null));
                    }
                });

        // TODO TJ what about preEnforcement? was done in DispatcherActor somehow .. e.g. for solution auth
        pubSubMediator.tell(
                DistPubSubAccess.send(ThingsSearchConstants.SEARCH_ACTOR_PATH, thingSearchCommand),
                getSender());
    }

    private void forwardToThingSearch(final ThingSearchSudoCommand<?> thingSearchSudoCommand) {
        pubSubMediator.tell(
                DistPubSubAccess.send(ThingsSearchConstants.SEARCH_ACTOR_PATH, thingSearchSudoCommand),
                getSender());
    }

    private void handleUnknownSignal(final Signal<?> signal) {

        final Signal<?> transformedSignal = signalTransformer.apply(signal);
        final String signalType = transformedSignal.getType();
        final DittoDiagnosticLoggingAdapter l = log.withCorrelationId(transformedSignal);
        l.error("Received signal <{}> which is not known how to be handled: {}",
                signalType, transformedSignal);
    }

}
