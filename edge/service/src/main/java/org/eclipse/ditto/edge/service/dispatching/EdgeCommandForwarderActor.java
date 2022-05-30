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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.things.api.ThingsMessagingConstants;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.thingsearch.api.ThingsSearchConstants;
import org.eclipse.ditto.thingsearch.api.commands.sudo.ThingSearchSudoCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
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

    private final DefaultNamespaceProvider defaultNamespaceProvider;

    @SuppressWarnings("unused")
    private EdgeCommandForwarderActor(final ActorRef pubSubMediator, final ShardRegions shardRegions,
            final DefaultNamespaceProvider defaultNamespaceProvider) {

        this.pubSubMediator = pubSubMediator;
        this.shardRegions = shardRegions;
        this.defaultNamespaceProvider = defaultNamespaceProvider;
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param pubSubMediator the PubSub mediator Actor.
     * @param shardRegions shard regions to use in order to dispatch different entity Signals to.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ShardRegions shardRegions,
            final DefaultNamespaceProvider defaultNamespaceProvider) {
        return Props.create(EdgeCommandForwarderActor.class, pubSubMediator, shardRegions, defaultNamespaceProvider);
    }

    @Override
    public Receive createReceive() {
        final Receive receiveExtension = EdgeCommandForwarderExtension.get(context().system())
                .getReceiveExtension(getContext());

        final Receive forwardingReceive = ReceiveBuilder.create()
                .match(MessageCommand.class, this::forwardToThings)
                .match(CreateThing.class, this::handleCreateThing)
                .match(ThingCommand.class, this::forwardToThings)
                .match(RetrieveThings.class, this::forwardToThingsAggregator)
                .match(SudoRetrieveThings.class, this::forwardToThingsAggregator)
                .match(CreatePolicy.class, this::handleCreatePolicy)
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

    private void handleCreateThing(final CreateThing createThing) {
        final Optional<ThingId> providedThingId = createThing.getThing().getEntityId();
        final ThingId namespacedThingId = providedThingId
                .map(thingId -> {
                    if (thingId.getNamespace().isEmpty()) {
                        final String defaultNamespace = defaultNamespaceProvider.getDefaultNamespace(createThing);
                        return ThingId.of(defaultNamespace, thingId.getName());
                    } else {
                        return thingId;
                    }
                })
                .orElseGet(() -> {
                    final String defaultNamespace = defaultNamespaceProvider.getDefaultNamespace(createThing);
                    return ThingId.inNamespaceWithRandomName(defaultNamespace);
                });
        final Thing thingWithNamespacedId = createThing.getThing().toBuilder().setId(namespacedThingId).build();
        @Nullable final JsonObject initialPolicy = createThing.getInitialPolicy().orElse(null);
        @Nullable final String policyIdOrPlaceholder = createThing.getPolicyIdOrPlaceholder().orElse(null);
        final CreateThing createThingWithNamespace =
                CreateThing.of(thingWithNamespacedId, initialPolicy, policyIdOrPlaceholder,
                        createThing.getDittoHeaders());
        forwardToThings(createThingWithNamespace);
    }

    private void forwardToThings(final MessageCommand<?, ?> messageCommand) {
        log.withCorrelationId(messageCommand)
                .info("Forwarding message command with ID <{}> and type <{}> to 'things' shard region",
                        messageCommand.getEntityId(), messageCommand.getType());
        shardRegions.things()
                .forward(messageCommand, getContext());
    }

    private void forwardToThings(final ThingCommand<?> thingCommand) {
        log.withCorrelationId(thingCommand)
                .info("Forwarding thing command with ID <{}> and type <{}> to 'things' shard region",
                        thingCommand.getEntityId(), thingCommand.getType());
        shardRegions.things()
                .forward(thingCommand, getContext());
    }

    private void forwardToThingsAggregator(final Command<?> command) {
        final DistributedPubSubMediator.Send pubSubMsg =
                DistPubSubAccess.send(ThingsMessagingConstants.THINGS_AGGREGATOR_ACTOR_PATH, command);
        pubSubMediator.forward(pubSubMsg, getContext());
    }

    private void handleCreatePolicy(final CreatePolicy createPolicy) {
        final Optional<PolicyId> providedPolicyId = createPolicy.getPolicy().getEntityId();
        final PolicyId namespacedPolicyId = providedPolicyId
                .map(policyId -> {
                    if (policyId.getNamespace().isEmpty()) {
                        final String defaultNamespace = defaultNamespaceProvider.getDefaultNamespace(createPolicy);
                        return PolicyId.of(defaultNamespace, policyId.getName());
                    } else {
                        return policyId;
                    }
                })
                .orElseGet(() -> {
                    final String defaultNamespace = defaultNamespaceProvider.getDefaultNamespace(createPolicy);
                    return PolicyId.inNamespaceWithRandomName(defaultNamespace);
                });
        final Policy policyWithNamespacedId = createPolicy.getPolicy().toBuilder().setId(namespacedPolicyId).build();
        final DittoHeaders dittoHeaders = createPolicy.getDittoHeaders();
        final CreatePolicy createPolicyWithNamespace = CreatePolicy.of(policyWithNamespacedId, dittoHeaders);
        forwardToPolicies(createPolicyWithNamespace);
    }

    private void forwardToPolicies(final PolicyCommand<?> policyCommand) {
        log.withCorrelationId(policyCommand)
                .info("Forwarding policy command with ID <{}> and type <{}> to 'policies' shard region",
                        policyCommand.getEntityId(), policyCommand.getType());
        shardRegions.policies()
                .forward(policyCommand, getContext());
    }

    private void forwardToConnectivity(final ConnectivityCommand<?> connectivityCommand) {
        if (connectivityCommand instanceof WithEntityId withEntityId) {
            log.withCorrelationId(connectivityCommand)
                    .info("Forwarding connectivity command with ID <{}> and type <{}> to 'connections' " +
                            "shard region", withEntityId.getEntityId(), connectivityCommand.getType());
            shardRegions.connections()
                    .forward(connectivityCommand, getContext());
        } else {
            log.withCorrelationId(connectivityCommand)
                    .error("Could not forward ConnectivityCommand not implementing WithEntityId to 'connections' " +
                            "shard region");
        }
    }

    private void forwardToThingSearch(final Command<?> command) {
        pubSubMediator.tell(
                DistPubSubAccess.send(ThingsSearchConstants.SEARCH_ACTOR_PATH, command),
                getSender());
    }

    private void handleUnknownSignal(final Signal<?> signal) {

        final String signalType = signal.getType();
        final DittoDiagnosticLoggingAdapter l = log.withCorrelationId(signal);
        l.error("Received signal <{}> which is not known how to be handled: {}",
                signalType, signal);
    }

}
