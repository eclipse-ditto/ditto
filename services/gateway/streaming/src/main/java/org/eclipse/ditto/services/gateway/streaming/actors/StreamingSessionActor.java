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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.namespaces.NamespaceReader;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.things.ModelBasedThingsFieldExpressionFactory;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.InvalidJwt;
import org.eclipse.ditto.services.gateway.streaming.RefreshSession;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.models.acks.AcknowledgementAggregatorActor;
import org.eclipse.ditto.services.models.acks.AcknowledgementForwarderActor;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.search.SubscriptionManager;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayWebsocketSessionClosedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayWebsocketSessionExpiredException;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestFromSubscription;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.SourceQueueWithComplete;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor handling a single streaming connection / session.
 */
final class StreamingSessionActor extends AbstractActor {

    private final JsonSchemaVersion jsonSchemaVersion;
    private final String connectionCorrelationId;
    private final String type;
    private final DittoProtocolSub dittoProtocolSub;
    private final SourceQueueWithComplete<SessionedJsonifiable> eventAndResponsePublisher;
    private final AcknowledgementConfig acknowledgementConfig;
    private final HeaderTranslator headerTranslator;
    private final ActorRef subscriptionManager;
    private final Set<StreamingType> outstandingSubscriptionAcks;
    private final Map<StreamingType, StreamingSession> streamingSessions;
    private final DittoDiagnosticLoggingAdapter logger;

    @Nullable private Cancellable sessionTerminationCancellable;
    private AuthorizationContext authorizationContext;

    @SuppressWarnings("unused")
    private StreamingSessionActor(final Connect connect,
            final DittoProtocolSub dittoProtocolSub,
            final SourceQueueWithComplete<SessionedJsonifiable> eventAndResponsePublisher,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Props subscriptionManagerProps) {

        jsonSchemaVersion = connect.getJsonSchemaVersion();
        connectionCorrelationId = connect.getConnectionCorrelationId();
        type = connect.getType();
        this.dittoProtocolSub = dittoProtocolSub;
        this.eventAndResponsePublisher = eventAndResponsePublisher;
        this.acknowledgementConfig = acknowledgementConfig;
        this.headerTranslator = headerTranslator;
        outstandingSubscriptionAcks = EnumSet.noneOf(StreamingType.class);
        authorizationContext = AuthorizationModelFactory.emptyAuthContext();
        streamingSessions = new EnumMap<>(StreamingType.class);
        logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        logger.setCorrelationId(connectionCorrelationId);
        connect.getSessionExpirationTime().ifPresent(expiration ->
                sessionTerminationCancellable = startSessionTimeout(expiration)
        );
        this.subscriptionManager =
                getContext().actorOf(subscriptionManagerProps, SubscriptionManager.ACTOR_NAME);

        eventAndResponsePublisher.watchCompletion()
                .whenComplete((done, error) -> getSelf().tell(Control.TERMINATED, getSelf()));
    }

    /**
     * Creates Akka configuration object Props for this StreamingSessionActor.
     *
     * @param connect the command to start a streaming session.
     * @param dittoProtocolSub manager of subscriptions.
     * @param eventAndResponsePublisher the source queue to push events and responses into.
     * @param acknowledgementConfig the config to apply for Acknowledgements.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param subscriptionManagerProps Props of the subscription manager for search protocol.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connect connect,
            final DittoProtocolSub dittoProtocolSub,
            final SourceQueueWithComplete<SessionedJsonifiable> eventAndResponsePublisher,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Props subscriptionManagerProps) {

        return Props.create(StreamingSessionActor.class, connect, dittoProtocolSub, eventAndResponsePublisher,
                acknowledgementConfig, headerTranslator, subscriptionManagerProps);
    }

    @Override
    public void postStop() {
        cancelSessionTimeout();
        logger.info("Closing <{}> streaming session.", type);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Acknowledgement.class, acknowledgement ->
                        potentiallyForwardToAckregator(acknowledgement, () -> forwardAcknowledgement(acknowledgement))
                )
                .match(ThingCommandResponse.class, response ->
                        potentiallyForwardToAckregator(response, () -> handleResponse(response))
                )
                .match(CommandResponse.class, this::handleResponse)
                .match(ThingEvent.class, event -> handleSignalsToStartAckForwarderFor(event, event.getEntityId()))
                .match(ThingModifyCommand.class, thingModifyCommand -> {
                    final ActorRef self = getSelf();
                    try {
                        AcknowledgementAggregatorActor.startAcknowledgementAggregator(getContext(), thingModifyCommand,
                                acknowledgementConfig,
                                headerTranslator,
                                responseSignal -> self.tell(responseSignal, self));
                    } catch (final DittoRuntimeException e) {
                        logger.withCorrelationId(thingModifyCommand)
                                .info("Got 'DittoRuntimeException' <{}> session during 'startAcknowledgementAggregator':" +
                                        " {}: <{}>", type, e.getClass().getSimpleName(), e.getMessage());
                        eventAndResponsePublisher.offer(SessionedJsonifiable.error(e));
                        return;
                    }
                    handleSignal(thingModifyCommand);
                })
                .match(Signal.class, this::handleSignal)
                .match(DittoRuntimeException.class, cre -> {
                    logger.withCorrelationId(cre)
                            .info("Got 'DittoRuntimeException' message in <{}> session, telling" +
                                    " EventAndResponsePublisher about it: {}", type, cre);
                    eventAndResponsePublisher.offer(SessionedJsonifiable.error(cre));
                })
                .match(StartStreaming.class, startStreaming -> {
                    authorizationContext = startStreaming.getAuthorizationContext();
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
                        eventAndResponsePublisher.offer(SessionedJsonifiable.error(e));
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
                    dittoProtocolSub.subscribe(currentStreamingTypes, authorizationContext.getAuthorizationSubjectIds(),
                            getSelf()).thenAccept(ack -> getSelf().tell(subscribeAck, getSelf()));
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
                        dittoProtocolSub.updateLiveSubscriptions(currentStreamingTypes,
                                authorizationContext.getAuthorizationSubjectIds(), getSelf())
                                .thenAccept(ack -> getSelf().tell(unsubscribeAck, getSelf()));
                    } else {
                        dittoProtocolSub.removeTwinSubscriber(getSelf(),
                                authorizationContext.getAuthorizationSubjectIds())
                                .thenAccept(ack -> getSelf().tell(unsubscribeAck, getSelf()));
                    }
                })
                .match(RefreshSession.class, refreshSession -> {
                    cancelSessionTimeout();
                    checkAuthorizationContextAndStartSessionTimer(refreshSession);
                })
                .match(InvalidJwt.class, invalidJwtToken -> cancelSessionTimeout())
                .match(AcknowledgeSubscription.class, msg -> acknowledgeSubscription(msg.getStreamingType()))
                .match(AcknowledgeUnsubscription.class, msg -> acknowledgeUnsubscription(msg.getStreamingType()))
                .matchEquals(Control.TERMINATED, terminated -> {
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

    private void potentiallyForwardToAckregator(final CommandResponse<?> response, final Runnable fallbackAction) {
        final ActorContext context = getContext();
        final Consumer<ActorRef> action = aggregator -> aggregator.forward(response, context);

        context.findChild(
                AcknowledgementAggregatorActor.determineActorName(response.getDittoHeaders())
        ).ifPresentOrElse(action, fallbackAction);
    }

    private void handleResponse(final CommandResponse<?> response) {
        logger.withCorrelationId(response)
                .debug("Got 'CommandResponse' message in <{}> session, telling EventAndResponsePublisher" +
                        " about it: {}", type, response);
        eventAndResponsePublisher.offer(SessionedJsonifiable.response(response));
    }

    private void forwardAcknowledgement(final Acknowledgement acknowledgement) {
        // the Acknowledgement is meant for someone else:
        final ActorContext context = getContext();
        context.findChild(AcknowledgementForwarderActor.determineActorName(acknowledgement.getDittoHeaders()))
                .ifPresentOrElse(
                        forwarder -> forwarder.forward(acknowledgement, context),
                        () -> logger.withCorrelationId(acknowledgement)
                                .info("Received Acknowledgement but no AcknowledgementForwarderActor " +
                                        "was present: <{}>", acknowledgement)
                );
    }

    private void handleSignalsToStartAckForwarderFor(final Signal<?> signal, final EntityIdWithType entityIdWithType) {
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        AcknowledgementForwarderActor.startAcknowledgementForwarder(getContext(), entityIdWithType,
                dittoHeaders, acknowledgementConfig);
        handleSignal(signal);
    }

    private void handleSignal(final Signal<?> signal) {
        logger.setCorrelationId(signal);
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        if (signal instanceof CreateSubscription || signal instanceof RequestFromSubscription ||
                signal instanceof CancelSubscription) {
            subscriptionManager.tell(signal, getSelf());
        } else if (signal instanceof SubscriptionEvent) {
            logger.debug("Got SubscriptionEvent <{}> in <{}> session, telling EventAndResponsePublisher about it: {}",
                    signal.getType(), type, signal);
            eventAndResponsePublisher.offer(SessionedJsonifiable.subscription((SubscriptionEvent<?>) signal));
        } else if (signal instanceof CommandResponse) {
            logger.debug("Got CommandResponse <{}> in <{}> session, telling EventAndResponsePublisher about it: {}",
                    signal.getType(), type, signal);
            eventAndResponsePublisher.offer(SessionedJsonifiable.response((CommandResponse<?>) signal));
        } else if (connectionCorrelationId.equals(dittoHeaders.getOrigin().orElse(null))) {
            logger.debug("Got Signal <{}> in <{}> session, but this was issued by this connection itself, not telling" +
                    " EventAndResponsePublisher about it", signal.getType(), type);
        } else {
            // check if this session is "allowed" to receive the Signal
            @Nullable final StreamingSession session = streamingSessions.get(determineStreamingType(signal));
            if (null != session && isSessionAllowedToReceiveSignal(signal, session)) {
                logger.debug("Got Signal <{}> in <{}> session, telling EventAndResponsePublisher about it: {}",
                        signal.getType(), type, signal);

                final DittoHeaders sessionHeaders = DittoHeaders.newBuilder()
                        .authorizationContext(authorizationContext)
                        .schemaVersion(jsonSchemaVersion)
                        .build();
                final SessionedJsonifiable sessionedJsonifiable =
                        SessionedJsonifiable.signal(signal, sessionHeaders, session);
                eventAndResponsePublisher.offer(sessionedJsonifiable);
            }
        }
        logger.setCorrelationId(connectionCorrelationId);
    }

    private boolean isSessionAllowedToReceiveSignal(final Signal<?> signal, final StreamingSession session) {
        final DittoHeaders headers = signal.getDittoHeaders();
        final boolean isAuthorizedToRead = authorizationContext.isAuthorized(headers.getReadGrantedSubjects(),
                headers.getReadRevokedSubjects());
        final boolean matchesNamespace = matchesNamespaces(signal, session);
        return isAuthorizedToRead && matchesNamespace;
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
        eventAndResponsePublisher.fail(gatewayWebsocketSessionExpiredException);
    }

    private void cancelSessionTimeout() {
        if (null != sessionTerminationCancellable) {
            sessionTerminationCancellable.cancel();
        }
    }

    private void checkAuthorizationContextAndStartSessionTimer(final RefreshSession refreshSession) {
        final AuthorizationContext newAuthorizationContext = refreshSession.getAuthorizationContext();
        if (!authorizationContext.equals(newAuthorizationContext)) {
            logger.debug("Authorization Context changed for WebSocket session <{}>. Terminating the session.",
                    connectionCorrelationId);
            final GatewayWebsocketSessionClosedException gatewayWebsocketSessionClosedException =
                    GatewayWebsocketSessionClosedException.newBuilder()
                            .dittoHeaders(DittoHeaders.newBuilder()
                                    .correlationId(connectionCorrelationId)
                                    .build())
                            .build();
            eventAndResponsePublisher.fail(gatewayWebsocketSessionClosedException);
        } else {
            sessionTerminationCancellable = startSessionTimeout(refreshSession.getSessionTimeout());
        }
    }

    private boolean matchesNamespaces(final Signal<?> signal, final StreamingSession session) {
        final List<String> namespaces = session.getNamespaces();
        final boolean result = namespaces.isEmpty() || namespaces.contains(namespaceFromId(signal));
        if (!result) {
            logger.debug("Signal does not match namespaces.");
        }
        return result;
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

    private void acknowledgeSubscription(final StreamingType streamingType) {
        if (outstandingSubscriptionAcks.contains(streamingType)) {
            outstandingSubscriptionAcks.remove(streamingType);
            eventAndResponsePublisher.offer(SessionedJsonifiable.ack(streamingType, true, connectionCorrelationId));
            logger.debug("Subscribed to Cluster <{}> in <{}> session.", streamingType, type);
        } else {
            logger.debug("Subscription already acked for type <{}> in <{}> session.", streamingType, type);
        }
    }

    private void acknowledgeUnsubscription(final StreamingType streamingType) {
        eventAndResponsePublisher.offer(SessionedJsonifiable.ack(streamingType, false, connectionCorrelationId));
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

    private enum Control {
        TERMINATED
    }

}
