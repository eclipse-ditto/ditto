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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

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
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.streaming.CloseStreamExceptionally;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.InvalidJwt;
import org.eclipse.ditto.services.gateway.streaming.RefreshSession;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
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
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor handling a single streaming connection / session.
 */
final class StreamingSessionActor extends AbstractActor {

    private final String connectionCorrelationId;
    private final String type;
    private final DittoProtocolSub dittoProtocolSub;
    private final ActorRef eventAndResponsePublisher;
    private final Set<StreamingType> outstandingSubscriptionAcks;
    private final Map<StreamingType, StreamingSession> streamingSessions;
    private final DittoDiagnosticLoggingAdapter logger;

    @Nullable private Cancellable sessionTerminationCancellable;
    private List<String> authorizationSubjects;

    @SuppressWarnings("unused")
    private StreamingSessionActor(final Connect connect,
            final DittoProtocolSub dittoProtocolSub,
            final ActorRef eventAndResponsePublisher) {

        this.connectionCorrelationId = connect.getConnectionCorrelationId();
        this.type = connect.getType();
        this.dittoProtocolSub = dittoProtocolSub;
        this.eventAndResponsePublisher = eventAndResponsePublisher;
        outstandingSubscriptionAcks = EnumSet.noneOf(StreamingType.class);
        authorizationSubjects = Collections.emptyList();
        streamingSessions = new EnumMap<>(StreamingType.class);
        connect.getSessionExpirationTime().ifPresent(expiration ->
                sessionTerminationCancellable = startSessionTimeout(expiration)
        );
        logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        logger.setCorrelationId(connectionCorrelationId);

        getContext().watch(eventAndResponsePublisher);
    }

    /**
     * Creates Akka configuration object Props for this StreamingSessionActor.
     *
     * @param connect the command to start a streaming session.
     * @param dittoProtocolSub manager of subscriptions.
     * @param eventAndResponsePublisher the {@link org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher} actor.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connect connect,
            final DittoProtocolSub dittoProtocolSub,
            final ActorRef eventAndResponsePublisher) {

        return Props.create(StreamingSessionActor.class, connect, dittoProtocolSub, eventAndResponsePublisher);
    }

    @Override
    public void postStop() {
        cancelSessionTimeout();
        logger.info("Closing <{}> streaming session.", type);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(CommandResponse.class, StreamingSessionActor::isTwinSignal, response -> {
                    logger.withCorrelationId(response)
                            .debug("Got 'CommandResponse' message in <{}> session, telling EventAndResponsePublisher" +
                                    " about it: {}", type, response);
                    eventAndResponsePublisher.forward(SessionedJsonifiable.response(response), getContext());
                })
                .match(Signal.class, this::handleSignal)
                .match(DittoRuntimeException.class, cre -> {
                    logger.withCorrelationId(cre)
                            .info("Got 'DittoRuntimeException' message in <{}> session, telling" +
                                    " EventAndResponsePublisher about it: {}", type, cre);
                    eventAndResponsePublisher.forward(SessionedJsonifiable.error(cre), getContext());
                })
                .match(StartStreaming.class, startStreaming -> {
                    authorizationSubjects = startStreaming.getAuthorizationContext().getAuthorizationSubjectIds();
                    logger.setCorrelationId(connectionCorrelationId);
                    Criteria criteria;
                    try {
                        criteria = startStreaming.getFilter()
                                .map(f -> parseCriteria(f, DittoHeaders.newBuilder()
                                        .correlationId(startStreaming.getConnectionCorrelationId())
                                        .build()))
                                .orElse(null);
                    } catch (final DittoRuntimeException e) {
                        logger.info("Got 'DittoRuntimeException' <{}> session during 'StartStreaming' processing:" +
                                " {}: <{}>", type, e.getClass().getSimpleName(), e.getMessage());
                        eventAndResponsePublisher.tell(SessionedJsonifiable.error(e), getSelf());
                        return;
                    }
                    final StreamingSession session = StreamingSession.of(startStreaming.getNamespaces(), criteria,
                            startStreaming.getExtraFields().orElse(null));
                    streamingSessions.put(startStreaming.getStreamingType(), session);


                    logger.debug("Got 'StartStreaming' message in <{}> session, subscribing for <{}> in Cluster ...",
                            type, startStreaming.getStreamingType().name());

                    outstandingSubscriptionAcks.add(startStreaming.getStreamingType());
                    // In Cluster: Subscribe
                    final AcknowledgeSubscription subscribeAck =
                            new AcknowledgeSubscription(startStreaming.getStreamingType());
                    final Collection<StreamingType> currentStreamingTypes = streamingSessions.keySet();
                    dittoProtocolSub.subscribe(currentStreamingTypes, authorizationSubjects, getSelf())
                            .thenAccept(ack -> getSelf().tell(subscribeAck, getSelf()));
                })
                .match(StopStreaming.class, stopStreaming -> {
                    logger.debug("Got 'StopStreaming' message in <{}> session, unsubscribing from <{}> in Cluster ...",
                            type, stopStreaming.getStreamingType().name());

                    streamingSessions.remove(stopStreaming.getStreamingType());

                    // In Cluster: Unsubscribe
                    final AcknowledgeUnsubscription unsubscribeAck =
                            new AcknowledgeUnsubscription(stopStreaming.getStreamingType());
                    final Collection<StreamingType> currentStreamingTypes = streamingSessions.keySet();
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
                    cancelSessionTimeout();
                    checkAuthorizationContextAndStartSessionTimer(refreshSession);
                })
                .match(InvalidJwt.class, invalidJwtToken -> cancelSessionTimeout())
                .match(AcknowledgeSubscription.class, msg ->
                        acknowledgeSubscription(msg.getStreamingType(), getSelf()))
                .match(AcknowledgeUnsubscription.class, msg ->
                        acknowledgeUnsubscription(msg.getStreamingType(), getSelf()))
                .match(Terminated.class, terminated -> {
                    logger.setCorrelationId(connectionCorrelationId);
                    logger.debug("EventAndResponsePublisher was terminated.");
                    // In Cluster: Unsubscribe from ThingEvents:
                    logger.info("<{}> connection was closed, unsubscribing from Streams in Cluster ...", type);

                    dittoProtocolSub.removeSubscriber(getSelf());

                    getContext()
                            .getSystem()
                            .scheduler()
                            .scheduleOnce(FiniteDuration.apply(1, TimeUnit.SECONDS), getSelf(),
                                    PoisonPill.getInstance(), getContext().dispatcher(), getSelf());
                })
                .matchAny(any -> logger.withCorrelationId(connectionCorrelationId)
                        .warning("Got unknown message in '{}' session: '{}'", type, any))
                .build();
    }

    private void handleSignal(final Signal<?> signal) {
        logger.setCorrelationId(signal);
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        if (connectionCorrelationId.equals(dittoHeaders.getOrigin().orElse(null))) {
            logger.debug("Got Signal <{}> in <{}> session, but this was issued by this connection itself, not telling" +
                    " EventAndResponsePublisher about it", signal.getType(), type);
        } else {
            // check if this session is "allowed" to receive the LiveSignal
            final StreamingSession session = streamingSessions.get(determineStreamingType(signal));
            if (session != null &&
                    authorizationSubjects != null &&
                    !Collections.disjoint(dittoHeaders.getReadSubjects(), authorizationSubjects)) {

                if (matchesNamespaces(signal, session)) {
                    if (matchesFilter(signal, session)) {
                        logger.debug("Got Signal <{}> in <{}> session, telling EventAndResponsePublisher about it: {}",
                                signal.getType(), type, signal);

                        final SessionedJsonifiable sessionedJsonifiable =
                                SessionedJsonifiable.signal(signal, authorizationSubjects,
                                        session.getExtraFields().orElse(null));
                        eventAndResponsePublisher.tell(sessionedJsonifiable, getSelf());
                    } else {
                        logger.debug("Signal does not match filter");
                    }
                } else {
                    logger.debug("Signal does not match namespaces");
                }
            }
        }
        logger.setCorrelationId(connectionCorrelationId);
    }

    private Cancellable startSessionTimeout(final Instant sessionExpirationTime) {
        final long timeout = sessionExpirationTime.minusMillis(Instant.now().toEpochMilli()).toEpochMilli();

        logger.debug("Starting session timeout - session will expire in {} ms", timeout);
        final FiniteDuration sessionExpirationTimeMillis = FiniteDuration.apply(timeout, TimeUnit.MILLISECONDS);
        return getContext().getSystem().getScheduler().scheduleOnce(sessionExpirationTimeMillis,
                this::handleSessionTimeout, getContext().getDispatcher());
    }

    private void handleSessionTimeout() {
        logger.info("Stopping WebSocket session for connection with ID <{}>.", connectionCorrelationId);
        final GatewayWebsocketSessionExpiredException gatewayWebsocketSessionExpiredException =
                GatewayWebsocketSessionExpiredException.newBuilder()
                        .dittoHeaders(DittoHeaders.newBuilder()
                                .correlationId(connectionCorrelationId)
                                .build())
                        .build();
        eventAndResponsePublisher.tell(
                CloseStreamExceptionally.getInstance(gatewayWebsocketSessionExpiredException, connectionCorrelationId),
                getSelf());
    }

    private void cancelSessionTimeout() {
        if (null != sessionTerminationCancellable) {
            sessionTerminationCancellable.cancel();
        }
    }

    private void checkAuthorizationContextAndStartSessionTimer(final RefreshSession refreshSession) {
        final List<String> newAuthorizationSubjects =
                refreshSession.getAuthorizationContext().getAuthorizationSubjectIds();
        if (!authorizationSubjects.equals(newAuthorizationSubjects)) {
            logger.debug("Authorization Context changed for WebSocket session <{}>. Terminating the session.",
                    connectionCorrelationId);
            final GatewayWebsocketSessionClosedException gatewayWebsocketSessionClosedException =
                    GatewayWebsocketSessionClosedException.newBuilder()
                            .dittoHeaders(DittoHeaders.newBuilder()
                                    .correlationId(connectionCorrelationId)
                                    .build())
                            .build();
            eventAndResponsePublisher.tell(CloseStreamExceptionally.getInstance(gatewayWebsocketSessionClosedException,
                    connectionCorrelationId), getSelf());
        } else {
            sessionTerminationCancellable = startSessionTimeout(refreshSession.getSessionTimeout());
        }
    }

    private boolean matchesNamespaces(final Signal<?> signal, final StreamingSession session) {
        final List<String> namespaces = session.getNamespaces();
        return namespaces.isEmpty() || namespaces.contains(namespaceFromId(signal));
    }

    private static StreamingType determineStreamingType(final Signal<?> signal) {
        final String channel = signal.getDittoHeaders().getChannel().orElse(TopicPath.Channel.TWIN.getName());
        final StreamingType streamingType;
        if (signal instanceof Event) {
            streamingType = channel.equals(TopicPath.Channel.TWIN.getName())
                    ? StreamingType.EVENTS
                    : StreamingType.LIVE_EVENTS;
        } else if (signal instanceof MessageCommand) {
            streamingType = StreamingType.MESSAGES;
        } else {
            streamingType = StreamingType.LIVE_COMMANDS;
        }
        return streamingType;
    }

    private static boolean isTwinSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel()
                .map(TopicPath.Channel.TWIN.getName()::equals)
                .orElse(true);
    }

    @Nullable
    private static String namespaceFromId(final WithId withId) {
        return NamespaceReader.fromEntityId(withId.getEntityId()).orElse(null);
    }

    private static Criteria parseCriteria(final String filter, final DittoHeaders dittoHeaders) {
        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory =
                new ModelBasedThingsFieldExpressionFactory();
        final QueryFilterCriteriaFactory queryFilterCriteriaFactory =
                new QueryFilterCriteriaFactory(criteriaFactory, fieldExpressionFactory);

        return queryFilterCriteriaFactory.filterCriteria(filter, dittoHeaders);
    }

    private boolean matchesFilter(final Signal<?> signal, final StreamingSession session) {
        final Optional<Criteria> criteria = session.getEventFilterCriteria();
        if (signal instanceof ThingEvent && criteria.isPresent()) {
            // currently only ThingEvents may be filtered with RQL
            return ThingEventToThingConverter.thingEventToThing((ThingEvent) signal)
                    .filter(thing -> ThingPredicateVisitor.apply(criteria.get()).test(thing))
                    .isPresent();
        } else {
            return true;
        }
    }

    private void acknowledgeSubscription(final StreamingType streamingType, final ActorRef self) {
        if (outstandingSubscriptionAcks.contains(streamingType)) {
            outstandingSubscriptionAcks.remove(streamingType);
            eventAndResponsePublisher.tell(SessionedJsonifiable.ack(streamingType, true, connectionCorrelationId),
                    self);
            logger.debug("Subscribed to Cluster <{}> in <{}> session.", streamingType, type);
        } else {
            logger.debug("Subscription already acked for type <{}> in <{}> session.", streamingType, type);
        }
    }

    private void acknowledgeUnsubscription(final StreamingType streamingType, final ActorRef self) {
        eventAndResponsePublisher.tell(SessionedJsonifiable.ack(streamingType, false, connectionCorrelationId), self);
        logger.debug("Unsubscribed from Cluster <{}> in <{}> session.", streamingType, type);
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
