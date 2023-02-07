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

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.streaming.StreamingSubscriptionCommand;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformers;
import org.eclipse.ditto.connectivity.api.ConnectivityMessagingConstants;
import org.eclipse.ditto.connectivity.api.commands.sudo.ConnectivitySudoCommand;
import org.eclipse.ditto.connectivity.model.ConnectivityConstants;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveAllConnectionIds;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.thingsearch.api.ThingsSearchConstants;
import org.eclipse.ditto.thingsearch.api.commands.sudo.ThingSearchSudoCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;

import com.typesafe.config.Config;

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

    private final ActorRef taskScheduler;

    @SuppressWarnings("unused")
    private EdgeCommandForwarderActor(final ActorRef pubSubMediator, final ShardRegions shardRegions) {

        this.pubSubMediator = pubSubMediator;
        this.shardRegions = shardRegions;
        final var actorSystem = getContext().getSystem();
        final Config config = actorSystem.settings().config();
        final var dittoScoped = DefaultScopedConfig.dittoScoped(config);
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(config);
        final var askWithRetryConfig = DefaultAskWithRetryConfig.of(dittoScoped, "ask-with-retry");
        this.signalTransformer = SignalTransformers.get(actorSystem, dittoExtensionsConfig);
        askWithRetryCommandForwarder = AskWithRetryCommandForwarder.get(actorSystem);
        aggregatorProxyActor = getContext().actorOf(ThingsAggregatorProxyActor.props(pubSubMediator),
                ThingsAggregatorProxyActor.ACTOR_NAME);
        taskScheduler =
                getContext().actorOf(EntityTaskScheduler.props(ACTOR_NAME), EntityTaskScheduler.ACTOR_NAME);
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
        final ActorSystem system = context().system();
        final Config extensionsConfig = ScopedConfig.dittoExtension(system.settings().config());
        final Receive receiveExtension = EdgeCommandForwarderExtension.get(system, extensionsConfig)
                .getReceiveExtension(getContext());

        final Receive forwardingReceive = ReceiveBuilder.create()
                .match(MessageCommand.class, this::forwardToThings)
                .match(MessageCommandResponse.class, this::forwardToThings)
                .match(ThingCommand.class, this::forwardToThings)
                .match(ThingCommandResponse.class, CommandResponse::isLiveCommandResponse, this::forwardToThings)
                .match(ThingCommandResponse.class, twinCommandResponse -> log.withCorrelationId(twinCommandResponse)
                        .warning("Ignoring ThingCommandResponse received on 'twin' channel: <{}>", twinCommandResponse))
                .match(ThingEvent.class, Event::isLiveEvent, this::forwardToThings)
                .match(ThingEvent.class, twinEvent -> log.withCorrelationId(twinEvent)
                        .warning("Ignoring ThingEvent received on 'twin' channel: <{}>", twinEvent)
                )
                .match(RetrieveThings.class, this::forwardToThingsAggregatorProxy)
                .match(SudoRetrieveThings.class, this::forwardToThingsAggregatorProxy)
                .match(PolicyCommand.class, this::forwardToPolicies)
                .match(RetrieveAllConnectionIds.class, this::forwardToConnectivityPubSub)
                .match(ConnectivityCommand.class, this::forwardToConnectivity)
                .match(ConnectivitySudoCommand.class, this::forwardToConnectivity)
                .match(ThingSearchCommand.class, this::forwardToThingSearch)
                .match(ThingSearchSudoCommand.class, this::forwardToThingSearch)
                .match(StreamingSubscriptionCommand.class,
                        src -> src.getEntityType().equals(ThingConstants.ENTITY_TYPE),
                        this::forwardToThings
                )
                .match(StreamingSubscriptionCommand.class,
                        src -> src.getEntityType().equals(PolicyConstants.ENTITY_TYPE),
                        this::forwardToPolicies
                )
                .match(StreamingSubscriptionCommand.class,
                        src -> src.getEntityType().equals(ConnectivityConstants.ENTITY_TYPE),
                        this::forwardToConnectivity
                )
                .match(Signal.class, this::handleUnknownSignal)
                .matchAny(m -> log.warning("Got unknown message: {}", m))
                .build();

        return receiveExtension.orElse(forwardingReceive);
    }

    private void forwardToThings(final Signal<?> thingSignal) {
        final ActorRef sender = getSender();
        final CompletionStage<Signal<?>> signalTransformationCs = applySignalTransformation(thingSignal, sender);

        scheduleTask(thingSignal, () -> signalTransformationCs.thenAccept(transformed -> {
            log.withCorrelationId(transformed)
                    .info("Forwarding thing signal with ID <{}> and type <{}> to 'things' shard region",
                            transformed instanceof WithEntityId withEntityId ? withEntityId.getEntityId() : null,
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
        }));
    }

    private void scheduleTask(final Signal<?> signal, final Supplier<CompletionStage<Void>> taskCsSupplier) {

        if (signal instanceof WithEntityId withEntityId) {
            final EntityId entityId = withEntityId.getEntityId();
            log.withCorrelationId(signal)
                    .debug("Scheduling signal transformation task for entityId <{}>", entityId);
            scheduleTaskForEntity(new EntityTaskScheduler.Task<>(entityId, taskCsSupplier));
        } else {
            log.withCorrelationId(signal)
                    .debug("Scheduling signal transformation task without entity");
            taskCsSupplier.get()
                    .whenComplete((aVoid, throwable) ->
                            log.withCorrelationId(signal)
                                    .debug("Scheduled task without entityId completed successfully: <{}>",
                                            throwable == null)
                    );
        }
    }

    private CompletionStage<Signal<?>> applySignalTransformation(final Signal<?> signal, final ActorRef sender) {
        return signalTransformer.apply(signal)
                .whenComplete((transformed, error) -> {
                    if (error != null) {
                        final var dre = DittoRuntimeException.asDittoRuntimeException(error,
                                reason -> DittoInternalErrorException.newBuilder()
                                        .dittoHeaders(signal.getDittoHeaders())
                                        .cause(reason)
                                        .build());
                        sender.tell(dre, ActorRef.noSender());
                    }
                });
    }

    private void scheduleTaskForEntity(final EntityTaskScheduler.Task<Void> task) {
        taskScheduler.tell(task, ActorRef.noSender());
    }

    private void forwardToThingsAggregatorProxy(final Command<?> command) {
        final ActorRef sender = getSender();
        final CompletionStage<Signal<?>> signalTransformationCs = applySignalTransformation(command, sender);
        scheduleTask(command,
                () -> signalTransformationCs.thenAccept(transformed -> aggregatorProxyActor.tell(transformed, sender)));
    }

    private void forwardToPolicies(final Signal<?> policySignal) {
        final ActorRef sender = getSender();
        final CompletionStage<Signal<?>> signalTransformationCs = applySignalTransformation(policySignal, sender);
        scheduleTask(policySignal, () -> signalTransformationCs
                .thenAccept(transformedSignal -> {
                    log.withCorrelationId(transformedSignal)
                            .info("Forwarding policy command with ID <{}> and type <{}> to 'policies' shard region",
                                    transformedSignal instanceof WithEntityId withEntityId ? withEntityId.getEntityId() :
                                            null,
                                    transformedSignal.getType());

                    if (transformedSignal instanceof Command<?> transformedCommand && isIdempotent(transformedCommand)) {
                        askWithRetryCommandForwarder.forwardCommand(transformedCommand,
                                shardRegions.policies(),
                                sender);
                    } else {
                        shardRegions.policies().tell(transformedSignal, sender);
                    }
                }));
    }

    public void forwardToConnectivityPubSub(final RetrieveAllConnectionIds cmd) {
        DistributedPubSubMediator.Send send =
                DistPubSubAccess.send(ConnectivityMessagingConstants.CONNECTION_ID_RETRIEVAL_ACTOR_PATH, cmd);
        pubSubMediator.tell(send, getSender());
    }

    private void forwardToConnectivity(final Command<?> connectivityCommand) {
        if (connectivityCommand instanceof WithEntityId withEntityId) {
            final ActorRef sender = getSender();
            final var signalTransformationCs = applySignalTransformation(connectivityCommand, sender);
            scheduleTask(connectivityCommand, () -> signalTransformationCs
                    .thenAccept(transformed -> {
                        final Command<?> transformedConnectivityCommand = (Command<?>) transformed;
                        log.withCorrelationId(transformedConnectivityCommand)
                                .info("Forwarding connectivity command with ID <{}> and type <{}> to 'connections' " +
                                                "shard region", withEntityId.getEntityId(),
                                        transformedConnectivityCommand.getType());

                        // don't retry connectivity commands
                        shardRegions.connections().tell(transformedConnectivityCommand, sender);
                    }));
        } else {
            log.withCorrelationId(connectivityCommand)
                    .error("Could not forward ConnectivityCommand not implementing WithEntityId to 'connections' " +
                            "shard region");
        }
    }

    private void forwardToThingSearch(final Command<?> command) {
        // don't use "ask with retry" as the search could take some time and we don't want to stress the search
        // by retrying a query several times if it took long
        pubSubMediator.tell(DistPubSubAccess.send(ThingsSearchConstants.SEARCH_ACTOR_PATH, command), getSender());
    }

    private void handleUnknownSignal(final Signal<?> signal) {
        applySignalTransformation(signal, sender())
                .thenAccept(transformedSignal -> {
                    final String signalType = transformedSignal.getType();
                    final DittoDiagnosticLoggingAdapter l = log.withCorrelationId(transformedSignal);
                    l.error("Received signal <{}> which is not known how to be handled: {}",
                            signalType, transformedSignal);
                });
    }

}
