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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.services.utils.distributedcache.actors.DeleteCacheEntry;
import org.eclipse.ditto.services.utils.distributedcache.actors.ReadConsistency;
import org.eclipse.ditto.services.utils.distributedcache.actors.WriteConsistency;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatistics;
import org.eclipse.ditto.signals.events.base.Event;

import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;

/**
 * Abstract base implementation for a command proxy.
 */
public abstract class AbstractProxyActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "proxy";

    /**
     * The name of the pub sub group for this actor.
     */
    static final String PUB_SUB_GROUP_NAME = "proxy";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef statisticsActor;

    AbstractProxyActor(final ActorRef pubSubMediator) {
        statisticsActor = getContext().actorOf(StatisticsActor.props(pubSubMediator), StatisticsActor.ACTOR_NAME);
    }

    protected abstract void addCommandBehaviour(final ReceiveBuilder receiveBuilder);

    protected abstract void addResponseBehaviour(final ReceiveBuilder receiveBuilder);

    protected abstract void addErrorBehaviour(final ReceiveBuilder receiveBuilder);

    protected abstract void deleteCacheEntry(final LookupEnforcerResponse response);

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
        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create();

        // common commands
        receiveBuilder
                .match(RetrieveStatistics.class, retrieveStatistics -> {
                    log.debug("Got 'RetrieveStatistics' message");
                    statisticsActor.forward(retrieveStatistics, getContext());
                });

        // specific commands
        addCommandBehaviour(receiveBuilder);

        // specific responses
        addResponseBehaviour(receiveBuilder);

        // common responses
        receiveBuilder
                .match(LookupEnforcerResponse.class, response -> response.getEnforcerRef().isPresent(), response -> {
                    final ActorRef enforcerRef = response.getEnforcerRef().get();
                    final LookupContext<?> lookupContext = response.getContext();
                    enforcerRef.tell(createShardedMessage(response), lookupContext.getInitialSender());
                    deleteCacheEntry(response);
                })
                .match(LookupEnforcerResponse.class, response -> response.getError().isPresent(), response -> {
                    final Throwable error = response.getError().get();
                    getLogger().info("Received Error during lookup of enforcer: {}", error.getMessage(), error);
                    final LookupContext<?> lookupContext = response.getContext();
                    final ActorRef initialSender = lookupContext.getInitialSender();
                    initialSender.tell(error, ActorRef.noSender());
                });

        // specific errors
        addErrorBehaviour(receiveBuilder);

        // common errors
        receiveBuilder
                .match(LookupEnforcerResponse.class, isOfType(Event.class), response -> {
                    final Event<?> event = getEvent(response);
                    getLogger().warning(
                            "Event of type <{}> with ID <{}> could not be dispatched as no enforcer could be" +
                                    " looked up! This should not happen and it most likely a bug.", event.getType(),
                            event.getId());
                })
                .match(LookupEnforcerResponse.class, response ->
                        getLogger().warning("EnforcerLookupActor.LookupEnforcerResponse could not be handled: {}",
                                response))
                .match(Status.Failure.class, failure -> {
                    Throwable cause = failure.cause();
                    if (cause instanceof JsonRuntimeException) {
                        cause = new DittoJsonException((RuntimeException) cause);
                    }
                    getSender().tell(cause, getSelf());
                })
                .match(DittoRuntimeException.class, cre -> getSender().tell(cre, getSelf()))
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        getLogger().debug("Successfully subscribed to distributed pub/sub on topic '{}'",
                                subscribeAck.subscribe().topic())
                )
                .matchAny(m -> getLogger().warning("Got unknown message, expected a 'Command': {}", m));

        return receiveBuilder.build();
    }

    protected DiagnosticLoggingAdapter getLogger() {
        return log;
    }

    protected static FI.TypedPredicate<LookupEnforcerResponse> isOfType(final CharSequence expectedType) {
        return lookupEnforcerResponse -> {
            final LookupContext<?> lookupContext = lookupEnforcerResponse.getContext();
            final Signal<?> signal = lookupContext.getInitialCommandOrEvent();
            return !isLiveSignal(signal) && Objects.equals(expectedType.toString(), signal.getType());
        };
    }

    protected static FI.TypedPredicate<LookupEnforcerResponse> isOfType(final Class<?> clazz) {
        return lookupEnforcerResponse -> {
            final LookupContext<?> lookupContext = lookupEnforcerResponse.getContext();
            final Signal<?> signal = lookupContext.getInitialCommandOrEvent();
            final Class<? extends Signal> signalClass = signal.getClass();
            return clazz.isAssignableFrom(signalClass);
        };
    }

    protected static Object createShardedMessage(final LookupEnforcerResponse lookupEnforcerResponse) {
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

    protected <T extends Signal<T>> FI.UnitApply<T> forwardToLocalEnforcerLookup(final ActorRef enforcerLookup) {
        return forwardToEnforcerLookup(enforcerLookup, ReadConsistency.LOCAL);
    }

    <T extends Signal<T>> FI.UnitApply<T> forwardToMajorityEnforcerLookup(final ActorRef enforcerLookup) {
        return forwardToEnforcerLookup(enforcerLookup, ReadConsistency.MAJORITY);
    }

    static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }

    static boolean isLiveSignalResponse(final Signal<?> signal) {
        return isLiveSignal(signal) && signal instanceof CommandResponse;
    }

    static Object getSignal(final LookupEnforcerResponse lookupEnforcerResponse) {
        return lookupEnforcerResponse.getContext().getInitialCommandOrEvent();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Event<T>> T getEvent(final LookupEnforcerResponse lookupEnforcerResponse) {
        final LookupContext<?> lookupContext = lookupEnforcerResponse.getContext();
        return (T) lookupContext.getInitialCommandOrEvent();
    }

    protected void deleteEntryFromCache(final LookupEnforcerResponse lookupEnforcerResponse,
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

    void notifySender(final Object message) {
        final ActorRef sender = getSender();
        sender.tell(message, getSelf());
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

}
