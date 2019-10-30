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
package org.eclipse.ditto.services.gateway.streaming.actors;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.namespaces.NamespaceReader;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.things.ModelBasedThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.streaming.InvalidJwtToken;
import org.eclipse.ditto.services.gateway.streaming.RefreshSession;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayWebsocketSessionClosedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayWebsocketSessionExpiredException;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingEventToThingConverter;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor handling a single streaming connection / session.
 */
final class StreamingSessionActor extends AbstractActor {

    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);

    private final String connectionCorrelationId;
    private final String type;
    private final DittoProtocolSub dittoProtocolSub;
    private final ActorRef eventAndResponsePublisher;
    private final Set<StreamingType> outstandingSubscriptionAcks;
    private Cancellable sessionTerminationCancellable;

    private List<String> authorizationSubjects;
    private final Map<StreamingType, List<String>> namespacesForStreamingTypes;
    private final Map<StreamingType, Criteria> eventFilterCriteriaForStreamingTypes;

    @SuppressWarnings("unused")
    private StreamingSessionActor(final String connectionCorrelationId, final String type,
            final DittoProtocolSub dittoProtocolSub, final ActorRef eventAndResponsePublisher,
            final Instant sessionExpirationTime) {
        this.connectionCorrelationId = connectionCorrelationId;
        this.type = type;
        this.dittoProtocolSub = dittoProtocolSub;
        this.eventAndResponsePublisher = eventAndResponsePublisher;
        outstandingSubscriptionAcks = new HashSet<>();
        authorizationSubjects = Collections.emptyList();
        namespacesForStreamingTypes = new EnumMap<>(StreamingType.class);
        eventFilterCriteriaForStreamingTypes = new EnumMap<>(StreamingType.class);

        getContext().watch(eventAndResponsePublisher);

        if (sessionExpirationTime != null) {
            sessionTerminationCancellable = startSessionTimeout(sessionExpirationTime);
        } else {
            sessionTerminationCancellable = null;
        }
    }

    /**
     * Creates Akka configuration object Props for this StreamingSessionActor.
     *
     * @param dittoProtocolSub manager of subscriptions.
     * @param eventAndResponsePublisher the {@link EventAndResponsePublisher} actor.
     * @return the Akka configuration Props object.
     */
    static Props props(final String connectionCorrelationId, final String type,
            final DittoProtocolSub dittoProtocolSub, final ActorRef eventAndResponsePublisher,
            final Instant sessionExpirationTime) {

        return Props.create(StreamingSessionActor.class, connectionCorrelationId, type, dittoProtocolSub,
                eventAndResponsePublisher, sessionExpirationTime);
    }

    @Override
    public void postStop() {
        LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
        sessionTerminationCancellable.cancel();
        logger.info("Closing '{}' streaming session: {}", type, connectionCorrelationId);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(CommandResponse.class, response -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, response);
                    logger.debug(
                            "Got 'CommandResponse' message in <{}> session, telling eventAndResponsePublisher about it: {}",
                            type, response);
                    eventAndResponsePublisher.forward(response, getContext());
                })
                .match(Signal.class, this::handleSignal)
                .match(DittoRuntimeException.class, cre -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, cre);
                    logger.info(
                            "Got 'DittoRuntimeException' message in <{}> session, telling eventAndResponsePublisher about it: {}",
                            type, cre);
                    eventAndResponsePublisher.forward(cre, getContext());
                })
                .match(StartStreaming.class, startStreaming -> {
                    authorizationSubjects = startStreaming.getAuthorizationContext().getAuthorizationSubjectIds();
                    namespacesForStreamingTypes
                            .put(startStreaming.getStreamingType(), startStreaming.getNamespaces());

                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);

                    try {
                        eventFilterCriteriaForStreamingTypes
                                .put(startStreaming.getStreamingType(), startStreaming.getFilter()
                                        .map(f -> parseCriteria(f, DittoHeaders.newBuilder()
                                                .correlationId(startStreaming.getConnectionCorrelationId())
                                                .build())
                                        )
                                        .orElse(null));
                    } catch (final DittoRuntimeException e) {
                        logger.info(
                                "Got 'DittoRuntimeException' <{}> session during 'StartStreaming' processing: {}: <{}>",
                                type, e.getClass().getSimpleName(), e.getMessage());
                        eventAndResponsePublisher.tell(e, getSelf());
                        return;
                    }

                    logger.debug("Got 'StartStreaming' message in <{}> session, subscribing for <{}> in Cluster..",
                            type, startStreaming.getStreamingType().name());

                    outstandingSubscriptionAcks.add(startStreaming.getStreamingType());
                    // In Cluster: Subscribe
                    final AcknowledgeSubscription subscribeAck =
                            new AcknowledgeSubscription(startStreaming.getStreamingType());
                    final Collection<StreamingType> currentStreamingTypes = namespacesForStreamingTypes.keySet();
                    dittoProtocolSub.subscribe(currentStreamingTypes, authorizationSubjects, getSelf())
                            .thenAccept(ack -> getSelf().tell(subscribeAck, getSelf()));
                })
                .match(StopStreaming.class, stopStreaming -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.debug("Got 'StopStreaming' message in <{}> session, unsubscribing from <{}> in Cluster..",
                            type, stopStreaming.getStreamingType().name());

                    namespacesForStreamingTypes.remove(stopStreaming.getStreamingType());
                    eventFilterCriteriaForStreamingTypes.remove(stopStreaming.getStreamingType());

                    // In Cluster: Unsubscribe
                    final AcknowledgeUnsubscription unsubscribeAck =
                            new AcknowledgeUnsubscription(stopStreaming.getStreamingType());
                    final Collection<StreamingType> currentStreamingTypes = namespacesForStreamingTypes.keySet();
                    if (stopStreaming.getStreamingType() != StreamingType.EVENTS) {
                        dittoProtocolSub.updateLiveSubscriptions(currentStreamingTypes, authorizationSubjects,
                                getSelf())
                                .thenAccept(ack -> getSelf().tell(unsubscribeAck, getSelf()));
                    } else {
                        dittoProtocolSub.removeTwinSubscriber(getSelf(), authorizationSubjects)
                                .thenAccept(ack -> getSelf().tell(unsubscribeAck, getSelf()));
                    }
                })
                .match(RefreshSession.class, refreshSession -> {
                    sessionTerminationCancellable.cancel();
                    checkAuthorizationContextAndStartSessionTimer(refreshSession);
                })
                .match(InvalidJwtToken.class, invalidJwtToken -> sessionTerminationCancellable.cancel())
                .match(AcknowledgeSubscription.class, msg ->
                        acknowledgeSubscription(msg.getStreamingType(), getSelf()))
                .match(AcknowledgeUnsubscription.class, msg ->
                        acknowledgeUnsubscription(msg.getStreamingType(), getSelf()))
                .match(Terminated.class, terminated -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.debug("eventAndResponsePublisher was terminated");
                    // In Cluster: Unsubscribe from ThingEvents:
                    logger.info("<{}> connection was closed, unsubscribing from Streams in Cluster..", type);

                    dittoProtocolSub.removeSubscriber(getSelf());

                    getContext().getSystem()
                            .scheduler()
                            .scheduleOnce(FiniteDuration.apply(1, TimeUnit.SECONDS), getSelf(),
                                    PoisonPill.getInstance(), getContext().dispatcher(), getSelf());
                })
                .matchAny(any -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.warning("Got unknown message in '{}' session: '{}'", type, any);
                })
                .build();
    }

    private void handleSignal(final Signal<?> signal) {
        LogUtil.enhanceLogWithCorrelationId(logger, signal);

        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        if (connectionCorrelationId.equals(dittoHeaders.getOrigin().orElse(null))) {
            logger.debug("Got Signal <{}> in <{}> session, " +
                    "but this was issued by this connection itself, not telling "
                    + "eventAndResponsePublisher about it", signal.getType(), type);
        } else {
            // check if this session is "allowed" to receive the LiveSignal
            if (authorizationSubjects != null &&
                    !Collections.disjoint(dittoHeaders.getReadSubjects(), authorizationSubjects)) {

                if (matchesNamespaces(signal)) {
                    if (matchesFilter(signal)) {
                        logger.debug("Got Signal <{}> in <{}> session, " +
                                        "telling eventAndResponsePublisher about it: {}",
                                signal.getType(), type, signal);

                        eventAndResponsePublisher.tell(signal, getSelf());
                    } else {
                        logger.debug("Signal does not match filter");
                    }
                } else {
                    logger.debug("Signal does not match namespaces");
                }
            }
        }
    }

    private Cancellable startSessionTimeout(final Instant sessionExpirationTime) {
        final long timeout = sessionExpirationTime.minusMillis(Instant.now().toEpochMilli()).toEpochMilli();

        logger.debug("Starting session timeout - session will expire in {} ms", timeout);
        final FiniteDuration sessionExpirationTimeMillis = FiniteDuration.apply(timeout, TimeUnit.MILLISECONDS);
        return getContext().getSystem().getScheduler().scheduleOnce(sessionExpirationTimeMillis,
                this::handleSessionTimeout, getContext().getDispatcher());
    }

    private void handleSessionTimeout() {
        logger.info("Stopping websocket session for connection with id: {}", connectionCorrelationId);
        eventAndResponsePublisher.tell(GatewayWebsocketSessionExpiredException.newBuilder()
                .dittoHeaders(DittoHeaders.newBuilder()
                        .correlationId(connectionCorrelationId)
                        .build())
                .build(), getSelf());
    }

    private void checkAuthorizationContextAndStartSessionTimer(final RefreshSession refreshSession) {
        final List<String> newAuthorizationSubjects =
                refreshSession.getAuthorizationContext().getAuthorizationSubjectIds();
        if (!authorizationSubjects.equals(newAuthorizationSubjects)) {
            logger.debug("Authorization Context changed for websocket session: <{}> - terminating the session",
                    connectionCorrelationId);
            eventAndResponsePublisher.tell(GatewayWebsocketSessionClosedException.newBuilder()
                    .dittoHeaders(DittoHeaders.newBuilder()
                            .correlationId(connectionCorrelationId)
                            .build())
                    .build(), getSelf());
        } else {
            sessionTerminationCancellable = startSessionTimeout(refreshSession.getSessionTimeout());
        }
    }

    private boolean matchesNamespaces(final Signal<?> signal) {
        final StreamingType streamingType = determineStreamingType(signal);

        final List<String> namespaces = Optional.ofNullable(namespacesForStreamingTypes.get(streamingType))
                .orElse(Collections.emptyList());
        return namespaces.isEmpty() || namespaces.contains(namespaceFromId(signal));
    }

    private static StreamingType determineStreamingType(final Signal<?> signal) {
        final String channel = signal.getDittoHeaders().getChannel().orElse(TopicPath.Channel.TWIN.getName());
        final StreamingType streamingType;
        if (signal instanceof Event) {
            streamingType = channel.equals(TopicPath.Channel.TWIN.getName()) ?
                    StreamingType.EVENTS : StreamingType.LIVE_EVENTS;
        } else if (signal instanceof MessageCommand) {
            streamingType = StreamingType.MESSAGES;
        } else {
            streamingType = StreamingType.LIVE_COMMANDS;
        }
        return streamingType;
    }

    private static String namespaceFromId(final WithId withId) {
        return NamespaceReader.fromEntityId(withId.getEntityId()).orElse(null);
    }

    /**
     * @throws org.eclipse.ditto.model.base.exceptions.InvalidRqlExpressionException if the filter string cannot be mapped to a
     * valid criterion
     */
    private Criteria parseCriteria(final String filter, final DittoHeaders dittoHeaders) {

        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory =
                new ModelBasedThingsFieldExpressionFactory();
        final QueryFilterCriteriaFactory queryFilterCriteriaFactory =
                new QueryFilterCriteriaFactory(criteriaFactory, fieldExpressionFactory);

        return queryFilterCriteriaFactory.filterCriteria(filter, dittoHeaders);
    }

    private boolean matchesFilter(final Signal<?> signal) {

        if (signal instanceof ThingEvent) {
            final StreamingType streamingType = determineStreamingType(signal);

            // currently only ThingEvents may be filtered with RQL
            return ThingEventToThingConverter.thingEventToThing((ThingEvent) signal)
                    .filter(thing -> doMatchFilter(streamingType, thing))
                    .isPresent();
        } else {
            return true;
        }
    }

    private boolean doMatchFilter(final StreamingType streamingType, final Thing thing) {

        final Optional<Criteria> criteria =
                Optional.ofNullable(eventFilterCriteriaForStreamingTypes.get(streamingType));

        return criteria
                .map(c -> ThingPredicateVisitor.apply(c).test(thing))
                .orElse(true); // let all events through if there was no criteria/filter set
    }

    private void acknowledgeSubscription(final StreamingType streamingType, final ActorRef self) {
        if (outstandingSubscriptionAcks.contains(streamingType)) {
            outstandingSubscriptionAcks.remove(streamingType);
            eventAndResponsePublisher.tell(new StreamingAck(streamingType, true), self);
            logger.debug("Subscribed to Cluster <{}> in <{}> session", streamingType, type);
        } else {
            logger.debug("Subscription already acked for type <{}> in <{}> session", streamingType, type);
        }
    }

    private void acknowledgeUnsubscription(final StreamingType streamingType, final ActorRef self) {
        eventAndResponsePublisher.tell(new StreamingAck(streamingType, false), self);
        logger.debug("Unsubscribed from Cluster <{}> in <{}> session", streamingType, type);
    }

    /**
     * Messages to self to perform an outstanding acknowledgement if not already acknowledged.
     */
    private abstract static class WithStreamingType {

        private final StreamingType streamingType;

        private WithStreamingType(final StreamingType streamingType) {
            this.streamingType = streamingType;
        }

        StreamingType getStreamingType() {
            return streamingType;
        }
    }

    private static final class AcknowledgeSubscription extends WithStreamingType {

        private AcknowledgeSubscription(final StreamingType streamingType) {
            super(streamingType);
        }
    }

    private static final class AcknowledgeUnsubscription extends WithStreamingType {

        private AcknowledgeUnsubscription(final StreamingType streamingType) {
            super(streamingType);
        }
    }
}
