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

import java.util.Objects;
import java.util.Optional;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.gateway.proxy.actors.handlers.CreateThingHandlerActor;
import org.eclipse.ditto.services.gateway.proxy.actors.handlers.ModifyPolicyHandlerActor;
import org.eclipse.ditto.services.gateway.proxy.actors.handlers.ModifyThingHandlerActor;
import org.eclipse.ditto.services.gateway.proxy.actors.handlers.RetrieveThingHandlerActor;
import org.eclipse.ditto.services.gateway.proxy.actors.handlers.ThingHandlerCreator;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoCommand;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveModifiedThingTags;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingsResponse;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.services.utils.distributedcache.actors.DeleteCacheEntry;
import org.eclipse.ditto.services.utils.distributedcache.actors.ReadConsistency;
import org.eclipse.ditto.services.utils.distributedcache.actors.WriteConsistency;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatistics;
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
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;
import akka.routing.FromConfig;


/**
 * Actor which delegates {@link Command}s to the appropriate receivers in the cluster.
 */
public final class ProxyActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "proxy";

    private static final String PUB_SUB_GROUP_NAME = "proxy";

    private static final String THINGS_SEARCH_ACTOR_PATH = "/user/thingsSearchRoot/thingsSearch";
    private static final String THINGS_PERSISTENCE_QUERIES_ACTOR_PATH = "/user/thingsRoot/persistenceQueries";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final ActorRef aclEnforcerShardRegion;
    private final ActorRef policyEnforcerShardRegion;

    private final ActorRef thingEnforcerLookup;
    private final ActorRef thingCacheFacade;

    private final ActorRef thingsAggregator;
    private final ActorRef statisticsActor;

    private ProxyActor(final ActorRef pubSubMediator,
            final ActorRef aclEnforcerShardRegion,
            final ActorRef policyEnforcerShardRegion,
            final ActorRef thingEnforcerLookup,
            final ActorRef thingCacheFacade) {
        this.pubSubMediator = pubSubMediator;
        this.aclEnforcerShardRegion = aclEnforcerShardRegion;
        this.policyEnforcerShardRegion = policyEnforcerShardRegion;

        this.thingEnforcerLookup = thingEnforcerLookup;
        this.thingCacheFacade = thingCacheFacade;

        thingsAggregator = getContext().actorOf(FromConfig.getInstance().props(
                ThingsAggregatorActor.props(getContext().self())), ThingsAggregatorActor.ACTOR_NAME);

        statisticsActor = getContext().actorOf(StatisticsActor.props(pubSubMediator), StatisticsActor.ACTOR_NAME);

        pubSubMediator.tell(
                new DistributedPubSubMediator.Subscribe(PolicyEvent.TYPE_PREFIX, PUB_SUB_GROUP_NAME, getSelf()),
                getSelf());
        pubSubMediator.tell(
                new DistributedPubSubMediator.Subscribe(ThingEvent.TYPE_PREFIX, PUB_SUB_GROUP_NAME, getSelf()),
                getSelf());
    }

    /**
     * Creates Akka configuration object Props for this ProxyActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use for subscribing for events.
     * @param aclEnforcerShardRegion the Actor ref of the acl enforcer shard region.
     * @param policyEnforcerShardRegion the Actor ref of the policy enforcer shard region.
     * @param thingEnforcerLookup the Actor ref to the thing enforcer lookup actor.
     * @param thingCacheFacade the Actor ref to the thing cache facade actor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef aclEnforcerShardRegion,
            final ActorRef policyEnforcerShardRegion,
            final ActorRef thingEnforcerLookup,
            final ActorRef thingCacheFacade) {
        return Props.create(ProxyActor.class, new Creator<ProxyActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ProxyActor create() throws Exception {
                return new ProxyActor(pubSubMediator, aclEnforcerShardRegion, policyEnforcerShardRegion,
                        thingEnforcerLookup, thingCacheFacade);
            }
        });
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(true, DeciderBuilder
                .match(NullPointerException.class, e -> {
                    log.error(e, "NullPointer in child actor - restarting it...", e.getMessage());
                    log.info("Restarting child...");
                    return SupervisorStrategy.restart();
                })
                .match(ActorKilledException.class, e -> {
                    log.error(e.getCause(), "ActorKilledException in child actor - stopping it...");
                    return SupervisorStrategy.stop();
                })
                .matchAny(e -> SupervisorStrategy.escalate())
                .build());
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveStatistics.class, retrieveStatistics -> {
                    log.debug("Got 'RetrieveStatistics' message");
                    statisticsActor.forward(retrieveStatistics, getContext());
                })

                /* Sudo Commands */
                .match(SudoRetrieveThings.class, command -> {
                    log.debug("Got 'SudoRetrieveThings' message, forwarding to the Things Aggregator");
                    if (command.getThingIds().isEmpty()) {
                        notifySender(SudoRetrieveThingsResponse.of(JsonArray.newBuilder().build(),
                                command.getDittoHeaders()));
                    } else {
                        thingsAggregator.forward(command, getContext());
                    }
                })
                .match(SudoRetrieveModifiedThingTags.class, command -> {
                    log.debug("Got 'SudoRetrieveModifiedThingTags' message, forwarding to the Things Persistence");
                    pubSubMediator.tell(
                            new DistributedPubSubMediator.Send(THINGS_PERSISTENCE_QUERIES_ACTOR_PATH, command),
                            getSender());
                })
                .match(SudoCommand.class, forwardToLocalEnforcerLookup(thingEnforcerLookup))

                /* Live Signals */
                .match(Signal.class, ProxyActor::isLive, forwardToLocalEnforcerLookup(thingEnforcerLookup))

                /* Policy Commands */
                .match(ModifyPolicy.class, command ->
                        getContext().actorOf(ModifyPolicyHandlerActor.props(policyEnforcerShardRegion))
                                .forward(command, getContext()))
                .match(PolicyCommand.class, command -> policyEnforcerShardRegion.forward(command, getContext()))
                .match(org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand.class, sudoCommand ->
                        policyEnforcerShardRegion.forward(sudoCommand, getContext()))

                /* Policy Events */
                .match(PolicyEvent.class, event -> {
                    LogUtil.enhanceLogWithCorrelationId(log, event);
                    log.debug("Got '{}' message, forwarding to the PolicyEnforcer", event.getType());
                    policyEnforcerShardRegion.tell(event, getSender());
                })

                /* Thing Commands */
                .match(RetrieveThings.class, command -> {
                    log.debug("Got 'RetrieveThings' message, forwarding to the Things Aggregator");
                    if (command.getThingIds().isEmpty()) {
                        notifySender(RetrieveThingsResponse.of(JsonFactory.newArray(), command.getDittoHeaders()));
                    } else {
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

                /* EnforcerLookup responses */
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
                    log.debug("Got 'RetrieveThing' message with a '{}' lookup: {}",
                            Policy.INLINED_FIELD_NAME,
                            retrieveThing);
                    final ActorRef actor = getThingHandlerActor(response, RetrieveThingHandlerActor::props);
                    actor.tell(getSignal(response), lookupContext.getInitialSender());
                })

                .match(LookupEnforcerResponse.class, response -> response.getEnforcerRef().isPresent(), response -> {
                    final ActorRef enforcerRef = response.getEnforcerRef().get();
                    final LookupContext<?> lookupContext = response.getContext();
                    enforcerRef.tell(createShardedMessage(response), lookupContext.getInitialSender());

                    // handle cache deletion
                    if (isOfType(ThingDeleted.TYPE).defined(response)) {
                        deleteEntryFromCache(response, thingCacheFacade);
                    }
                })

                .match(LookupEnforcerResponse.class, response -> response.getError().isPresent(), response -> {
                    final Throwable error = response.getError().get();
                    log.info("Received Error during lookup of enforcer: {}", error.getMessage(), error);
                    final LookupContext<?> lookupContext = response.getContext();
                    final ActorRef initialSender = lookupContext.getInitialSender();
                    initialSender.tell(error, ActorRef.noSender());
                })

                .match(LookupEnforcerResponse.class, isOfType(ThingCommand.class), response -> {
                    final LookupContext<?> lookupContext = response.getContext();
                    final ThingCommand thingCommand = (ThingCommand) lookupContext.getInitialCommandOrEvent();
                    log.info("Command of type <{}> with ID <{}> could not be dispatched as no enforcer could be" +
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
                    log.info("Command of type <{}> with ID <{}> could not be dispatched as no enforcer could be" +
                                    " looked up! Answering with ThingNotAccessibleException.", messageCommand.getType(),
                            messageCommand.getId());
                    final ActorRef initialSender = lookupContext.getInitialSender();
                    final Exception exception = ThingNotAccessibleException.newBuilder(messageCommand.getId())
                            .dittoHeaders(messageCommand.getDittoHeaders())
                            .build();
                    initialSender.tell(exception, ActorRef.noSender());
                })

                .match(LookupEnforcerResponse.class, isOfType(Event.class), response -> {
                    final Event<?> event = getEvent(response);
                    log.warning("Event of type <{}> with ID <{}> could not be dispatched as no enforcer could be" +
                                    " looked up! This should not happen and it most likely a bug.", event.getType(),
                            event.getId());
                })
                .match(LookupEnforcerResponse.class, response ->
                        log.warning("EnforcerLookupActor.LookupEnforcerResponse could not be handled: {}", response))
                .match(Status.Failure.class, failure -> {
                    Throwable cause = failure.cause();
                    if (cause instanceof JsonRuntimeException) {
                        cause = new DittoJsonException((RuntimeException) cause);
                    }
                    getSender().tell(cause, getSelf());
                })
                .match(DittoRuntimeException.class, cre -> getSender().tell(cre, getSelf()))
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        log.debug("Successfully subscribed to distributed pub/sub on topic '{}'",
                                subscribeAck.subscribe().topic())
                )
                .matchAny(m -> log.warning("Got unknown message, expected a 'Command': {}", m))
                .build();
    }

    private <T extends Signal<T>> FI.UnitApply<T> forwardToLocalEnforcerLookup(final ActorRef enforcerLookup) {
        return forwardToEnforcerLookup(enforcerLookup, ReadConsistency.LOCAL);
    }

    private <T extends Signal<T>> FI.UnitApply<T> forwardToMajorityEnforcerLookup(final ActorRef enforcerLookup) {
        return forwardToEnforcerLookup(enforcerLookup, ReadConsistency.MAJORITY);
    }

    private <T extends Signal<T>> FI.UnitApply<T> forwardToEnforcerLookup(
            final ActorRef enforcerLookup,
            final ReadConsistency readConsistency) {

        return signal -> {
            LogUtil.enhanceLogWithCorrelationId(log, signal);
            log.debug("Got <{}>. Forwarding to <{}>.", signal.getName(), enforcerLookup.path().name());
            final LookupContext<T> lookupContext = LookupContext.getInstance(signal, getSender(), getSelf());
            final LookupEnforcer lookupEnforcer = new LookupEnforcer(signal.getId(), lookupContext, readConsistency);
            enforcerLookup.tell(lookupEnforcer, getSelf());
        };
    }


    private static FI.TypedPredicate<LookupEnforcerResponse> isOfType(final CharSequence expectedType) {
        return lookupEnforcerResponse -> {
            final LookupContext<?> lookupContext = lookupEnforcerResponse.getContext();
            final Signal<?> signal = lookupContext.getInitialCommandOrEvent();
            return !isLive(signal) && Objects.equals(expectedType.toString(), signal.getType());
        };
    }

    private static FI.TypedPredicate<LookupEnforcerResponse> isOfType(final Class<?> clazz) {
        return lookupEnforcerResponse -> {
            final LookupContext<?> lookupContext = lookupEnforcerResponse.getContext();
            final Signal<?> signal = lookupContext.getInitialCommandOrEvent();
            final Class<? extends Signal> signalClass = signal.getClass();
            return clazz.isAssignableFrom(signalClass);
        };
    }

    private static boolean isLive(final Signal<?> signal) {
        return "LIVE".equals(signal.getDittoHeaders().get("channel"));
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

    @SuppressWarnings("unchecked")
    private static <T extends Event<T>> T getEvent(final LookupEnforcerResponse lookupEnforcerResponse) {
        final LookupContext<?> lookupContext = lookupEnforcerResponse.getContext();
        return (T) lookupContext.getInitialCommandOrEvent();
    }

    private ActorRef getThingHandlerActor(final LookupEnforcerResponse lookupEnforcerResponse,
            final ThingHandlerCreator thingHandlerCreator) {

        final ActorRef enforcerShard = lookupEnforcerResponse.getEnforcerRef().orElse(null);
        final String enforcerShardId = lookupEnforcerResponse.getShardId().orElse(null);

        final Props props = thingHandlerCreator.props(enforcerShard, enforcerShardId, aclEnforcerShardRegion,
                policyEnforcerShardRegion);

        return getContext().actorOf(props);
    }

    private static Object getSignal(final LookupEnforcerResponse lookupEnforcerResponse) {
        return lookupEnforcerResponse.getContext().getInitialCommandOrEvent();
    }

    private static Object createShardedMessage(final LookupEnforcerResponse lookupEnforcerResponse) {
        final LookupContext<?> lookupContext = lookupEnforcerResponse.getContext();
        final Signal<?> signal = lookupContext.getInitialCommandOrEvent();

        Object result = signal;

        final Optional<String> shardIdOptional = lookupEnforcerResponse.getShardId();
        if (shardIdOptional.isPresent()) {
            final String shardId = shardIdOptional.get();
            final JsonSchemaVersion implementedSchemaVersion = signal.getImplementedSchemaVersion();
            final JsonObject signalJsonObject = signal.toJson(implementedSchemaVersion, FieldType.regularOrSpecial());
            result = ShardedMessageEnvelope.of(shardId, signal.getType(), signalJsonObject, signal.getDittoHeaders());
        }

        return result;
    }

    private void deleteEntryFromCache(final LookupEnforcerResponse lookupEnforcerResponse,
            final ActorRef cacheFacade) {

        final Event<?> event = getEvent(lookupEnforcerResponse);
        final Optional<CacheEntry> cacheEntryToDeleteOptional = lookupEnforcerResponse.getCacheEntry();
        if (cacheEntryToDeleteOptional.isPresent()) {
            final DeleteCacheEntry deleteCacheEntry =
                    new DeleteCacheEntry(event.getId(), cacheEntryToDeleteOptional.get(), event.getRevision(),
                            WriteConsistency.LOCAL);
            cacheFacade.tell(deleteCacheEntry, ActorRef.noSender());
        } else {
            log.error("Attempting to delete nonexistent cache entry <{}>!", lookupEnforcerResponse);
        }
    }

    private void notifySender(final Object message) {
        final ActorRef sender = getSender();
        sender.tell(message, getSelf());
    }

}
