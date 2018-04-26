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
package org.eclipse.ditto.services.gateway.proxy.actors;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.gateway.proxy.actors.handlers.CreateThingHandlerActor;
import org.eclipse.ditto.services.gateway.proxy.actors.handlers.ModifyPolicyHandlerActor;
import org.eclipse.ditto.services.gateway.proxy.actors.handlers.ModifyThingHandlerActor;
import org.eclipse.ditto.services.gateway.proxy.actors.handlers.RetrieveThingHandlerActor;
import org.eclipse.ditto.services.gateway.proxy.actors.handlers.ThingHandlerCreator;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoCommand;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingsResponse;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.ThingSearchSudoCommand;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;
import akka.routing.FromConfig;

/**
 * Abstract implementation of {@link AbstractProxyActor} for all {@link org.eclipse.ditto.signals.commands.base.Command}s
 * related to {@link org.eclipse.ditto.model.things.Thing}s.
 */
public abstract class AbstractThingProxyActor extends AbstractProxyActor {

    private static final String THINGS_SEARCH_ACTOR_PATH = "/user/thingsSearchRoot/thingsSearch";

    private final ActorRef pubSubMediator;
    private final ActorRef aclEnforcerShardRegion;
    private final ActorRef devOpsCommandsActor;
    private final ActorRef policyEnforcerShardRegion;
    private final ActorRef thingEnforcerLookup;
    private final ActorRef thingCacheFacade;
    private final ActorRef thingsAggregator;

    protected AbstractThingProxyActor(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
            final ActorRef aclEnforcerShardRegion,
            final ActorRef policyEnforcerShardRegion,
            final ActorRef thingEnforcerLookup,
            final ActorRef thingCacheFacade) {
        super(pubSubMediator);

        this.pubSubMediator = pubSubMediator;
        this.devOpsCommandsActor = devOpsCommandsActor;
        this.policyEnforcerShardRegion = policyEnforcerShardRegion;
        this.aclEnforcerShardRegion = aclEnforcerShardRegion;
        this.thingCacheFacade = thingCacheFacade;
        this.thingEnforcerLookup = thingEnforcerLookup;

        thingsAggregator = getContext().actorOf(FromConfig.getInstance().props(
                ThingsAggregatorActor.props(getContext().self())), ThingsAggregatorActor.ACTOR_NAME);

        pubSubMediator.tell(
                new DistributedPubSubMediator.Subscribe(PolicyEvent.TYPE_PREFIX, PUB_SUB_GROUP_NAME, getSelf()),
                getSelf());
        pubSubMediator.tell(
                new DistributedPubSubMediator.Subscribe(ThingEvent.TYPE_PREFIX, PUB_SUB_GROUP_NAME, getSelf()),
                getSelf());
    }

    @Override
    protected void addCommandBehaviour(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                /* DevOps Commands */
                .match(DevOpsCommand.class, command -> {
                    LogUtil.enhanceLogWithCorrelationId(getLogger(), command);
                    getLogger().debug("Got 'DevOpsCommand' message <{}>, forwarding to local devOpsCommandsActor",
                            command.getType());
                    devOpsCommandsActor.forward(command, getContext());
                })

                /* Sudo Commands */
                .match(SudoRetrieveThings.class, command -> {
                    getLogger().debug("Got 'SudoRetrieveThings' message, forwarding to the Things Aggregator");
                    if (command.getThingIds().isEmpty()) {
                        getLogger().debug("Got 'SudoRetrieveThings' message with no ThingIds");
                        notifySender(SudoRetrieveThingsResponse.of(JsonArray.newBuilder().build(),
                                command.getDittoHeaders()));
                    } else {
                        getLogger().debug("Got 'SudoRetrieveThings' message, forwarding to the Things Aggregator");
                        thingsAggregator.forward(command, getContext());
                    }
                })
                .match(SudoCommand.class, forwardToLocalEnforcerLookup(thingEnforcerLookup))
                .match(org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand.class,
                        forwardToLocalEnforcerLookup(thingEnforcerLookup))

                /* Live Signal Responses - directly forward to sender */
                .match(Signal.class, ProxyActor::isLiveSignalResponse, signal -> {
                    // forward to response child actor which holds the original sender
                    signal.getDittoHeaders()
                            .getCorrelationId()
                            .map(this::encodeActorName)
                            .map(correlationId -> getContext().getChild(correlationId))
                            .ifPresent(childActor -> childActor.forward(signal, getContext()));
                })

                /* Live Signals */
                .match(Signal.class, ProxyActor::isLiveSignal,
                        compose(createResponseActor(), forwardToLocalEnforcerLookup(thingEnforcerLookup)))

                /* Policy Commands */
                .match(ModifyPolicy.class, command ->
                        getContext().actorOf(ModifyPolicyHandlerActor.props(policyEnforcerShardRegion))
                                .forward(command, getContext()))
                .match(PolicyCommand.class, command -> policyEnforcerShardRegion.forward(command, getContext()))
                .match(org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand.class, sudoCommand ->
                        policyEnforcerShardRegion.forward(sudoCommand, getContext()))

                /* Policy Events */
                .match(PolicyEvent.class, event -> {
                    LogUtil.enhanceLogWithCorrelationId(getLogger(), event);
                    getLogger().debug("Got '{}' message, forwarding to the PolicyEnforcer", event.getType());
                    policyEnforcerShardRegion.tell(event, getSender());
                })

                /* Thing Commands */
                .match(RetrieveThings.class, command -> {
                    if (command.getThingIds().isEmpty()) {
                        getLogger().debug("Got 'RetrieveThings' message with no ThingIds");
                        notifySender(RetrieveThingsResponse.of(JsonFactory.newArray(),
                                command.getNamespace().orElse(null), command.getDittoHeaders()));
                    } else {
                        getLogger().debug("Got 'RetrieveThings' message, forwarding to the Things Aggregator");
                        thingsAggregator.forward(command, getContext());
                    }
                })
                .match(ThingCommand.class, forwardToLocalEnforcerLookup(thingEnforcerLookup))
                .match(MessageCommand.class, forwardToLocalEnforcerLookup(thingEnforcerLookup))

                /* Thing Events */
                /* use MAJORITY for ThingDeleted events, because ThingsShardRegion no longer contains any
                   information regarding the authorization of this event (because the Thing is deleted.
                 */
                .match(ThingDeleted.class, forwardToMajorityEnforcerLookup(thingEnforcerLookup))
                /* other events are dispatched according to the local cache because enforcer-lookup-function
                   will be able to retrieve the current state of the thing in case of cache miss.
                */
                .match(ThingEvent.class, forwardToLocalEnforcerLookup(thingEnforcerLookup))

                /* Search Commands */
                .match(ThingSearchCommand.class, command -> pubSubMediator.tell(
                        new DistributedPubSubMediator.Send(THINGS_SEARCH_ACTOR_PATH, command), getSender()))

                .match(ThingSearchSudoCommand.class, command -> pubSubMediator.tell(
                        new DistributedPubSubMediator.Send(THINGS_SEARCH_ACTOR_PATH, command), getSender()))
        ;
    }

    @Override
    protected void addResponseBehaviour(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                .match(LookupEnforcerResponse.class, isOfType(CreateThing.TYPE), response -> {
                    final LookupContext<?> lookupContext = response.getContext();
                    final ActorRef actor = getThingHandlerActor(response, CreateThingHandlerActor::props);
                    actor.tell(getSignal(response), lookupContext.getInitialSender());
                })
                .match(LookupEnforcerResponse.class, isOfType(ModifyThing.TYPE), response -> {
                    final LookupContext<?> lookupContext = response.getContext();
                    final ActorRef actor = getThingHandlerActor(response, ModifyThingHandlerActor::props);
                    actor.tell(getSignal(response), lookupContext.getInitialSender());
                })
                .match(LookupEnforcerResponse.class, isRetrieveThingWithAggregationNeeded(), response -> {
                    final LookupContext<?> lookupContext = response.getContext();
                    final RetrieveThing retrieveThing = (RetrieveThing) lookupContext.getInitialCommandOrEvent();
                    getLogger().debug("Got 'RetrieveThing' message with a '{}' lookup: {}",
                            Policy.INLINED_FIELD_NAME,
                            retrieveThing);
                    final ActorRef actor = getThingHandlerActor(response, RetrieveThingHandlerActor::props);
                    actor.tell(getSignal(response), lookupContext.getInitialSender());
                });
    }

    @Override
    protected void addErrorBehaviour(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                .match(LookupEnforcerResponse.class, isOfType(ThingCommand.class), response -> {
                    final LookupContext<?> lookupContext = response.getContext();
                    final ThingCommand thingCommand = (ThingCommand) lookupContext.getInitialCommandOrEvent();
                    getLogger().info(
                            "Command of type <{}> with ID <{}> could not be dispatched as no enforcer could be" +
                                    " looked up! Answering with ThingNotAccessibleException.", thingCommand.getType(),
                            thingCommand.getId());
                    final ActorRef initialSender = lookupContext.getInitialSender();
                    final Exception exception = ThingNotAccessibleException.newBuilder(thingCommand.getId())
                            .dittoHeaders(thingCommand.getDittoHeaders())
                            .build();
                    initialSender.tell(exception, ActorRef.noSender());
                })
                .match(LookupEnforcerResponse.class, isOfType(MessageCommand.class), response -> {
                    final LookupContext<?> lookupContext = response.getContext();
                    final MessageCommand messageCommand = (MessageCommand) lookupContext.getInitialCommandOrEvent();
                    getLogger().info(
                            "Command of type <{}> with ID <{}> could not be dispatched as no enforcer could be" +
                                    " looked up! Answering with ThingNotAccessibleException.", messageCommand.getType(),
                            messageCommand.getId());
                    final ActorRef initialSender = lookupContext.getInitialSender();
                    final Exception exception = ThingNotAccessibleException.newBuilder(messageCommand.getId())
                            .dittoHeaders(messageCommand.getDittoHeaders())
                            .build();
                    initialSender.tell(exception, ActorRef.noSender());
                });
    }

    @Override
    protected void deleteCacheEntry(final LookupEnforcerResponse response) {
        if (isOfType(ThingDeleted.TYPE).defined(response)) {
            deleteEntryFromCache(response, thingCacheFacade);
        }
    }

    @SafeVarargs
    private final FI.UnitApply<Signal> compose(final FI.UnitApply<Signal>... pfs) {
        return signal -> {
            for (final FI.UnitApply<Signal> pf : pfs) {
                pf.apply(signal);
            }
        };
    }

    private <T extends Signal<T>> FI.UnitApply<T> createResponseActor() {
        return signal ->
                signal.getDittoHeaders()
                        .getCorrelationId()
                        .map(this::encodeActorName)
                        .ifPresent(this::startResponseChildActor);
    }

    private String encodeActorName(final String correlationId) {
        try {
            return URLEncoder.encode(correlationId, StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException exception) {
            throw new IllegalStateException("Unsupported encoding", exception);
        }
    }

    private ActorRef startResponseChildActor(final String correlationId) {
        return getContext().actorOf(CommandResponseActor.props(correlationId, getSender()), correlationId);
    }

    private static FI.TypedPredicate<LookupEnforcerResponse> isRetrieveThingWithAggregationNeeded() {
        return lookupEnforcerResponse -> {
            final FI.TypedPredicate<LookupEnforcerResponse> isOfTypePredicate = isOfType(RetrieveThing.TYPE);
            if (isOfTypePredicate.defined(lookupEnforcerResponse)) {
                final LookupContext<?> lookupContext = lookupEnforcerResponse.getContext();
                final RetrieveThing retrieveThing = (RetrieveThing) lookupContext.getInitialCommandOrEvent();
                return RetrieveThingHandlerActor.checkIfAggregationIsNeeded(retrieveThing);
            }
            return false;
        };
    }

    private ActorRef getThingHandlerActor(final LookupEnforcerResponse lookupEnforcerResponse,
            final ThingHandlerCreator thingHandlerCreator) {

        final ActorRef enforcerShard = lookupEnforcerResponse.getEnforcerRef().orElse(null);
        final String enforcerShardId = lookupEnforcerResponse.getShardId().orElse(null);

        final Props props = thingHandlerCreator.props(enforcerShard, enforcerShardId, aclEnforcerShardRegion,
                policyEnforcerShardRegion);

        return getContext().actorOf(props);
    }

}
