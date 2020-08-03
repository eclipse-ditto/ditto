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
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.namespaces.NamespaceReader;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.things.ModelBasedThingsFieldExpressionFactory;
import org.eclipse.ditto.model.things.WithThingId;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.streaming.CloseStreamExceptionally;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.IncomingSignal;
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
import org.eclipse.ditto.signals.commands.messages.acks.MessageCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.acks.ThingLiveCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.acks.ThingModifyCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestFromSubscription;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor handling a single streaming connection / session.
 */
final class StreamingSessionActor extends AbstractActor {

    private static final String START_ACK_AGGREGATOR_ERROR_MSG_TEMPLATE =
            "Got 'DittoRuntimeException' <{}> session during 'startAcknowledgementAggregator': {}: <{}>";
    private final JsonSchemaVersion jsonSchemaVersion;
    private final String connectionCorrelationId;
    private final String type;
    private final DittoProtocolSub dittoProtocolSub;
    private final ActorRef eventAndResponsePublisher;
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
            final ActorRef eventAndResponsePublisher,
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

        getContext().watch(eventAndResponsePublisher);
    }

    /**
     * Creates Akka configuration object Props for this StreamingSessionActor.
     *
     * @param connect the command to start a streaming session.
     * @param dittoProtocolSub manager of subscriptions.
     * @param eventAndResponsePublisher the {@link EventAndResponsePublisher} actor.
     * @param acknowledgementConfig the config to apply for Acknowledgements.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param subscriptionManagerProps Props of the subscription manager for search protocol.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connect connect,
            final DittoProtocolSub dittoProtocolSub,
            final ActorRef eventAndResponsePublisher,
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
        return createIncomingSignalBehavior()
                .orElse(createOutgoingSignalBehavior())
                .orElse(createPubSubBehavior())
                .orElse(createSelfTerminationBehavior())
                .orElse(logUnknownMessage(Function.identity()));
    }

    private Receive createIncomingSignalBehavior() {
        final PartialFunction<Object, Object> stripEnvelope = new PFBuilder<>()
                .match(IncomingSignal.class, IncomingSignal::getSignal)
                .build();

        // TODO: consider merging the cases
        final PartialFunction<Object, Object> setAckRequest = new PFBuilder<>()
                .match(MessageCommand.class, MessageCommandAckRequestSetter.getInstance()::apply)
                .match(ThingCommand.class, this::isLiveSignal, ThingLiveCommandAckRequestSetter.getInstance()::apply)
                .match(ThingModifyCommand.class, this::isResponseRequired,
                        ThingModifyCommandAckRequestSetter.getInstance()::apply)
                .matchAny(x -> x)
                .build();

        final PartialFunction<Object, Object> startAckregator = new PFBuilder<>()
                .match(Signal.class, AcknowledgementAggregatorActor::shouldStartForIncoming, this::startAckregator)
                .matchAny(x -> x)
                .build();

        // TODO: specialize handleSignal for incoming and outgoing
        final Receive signalBehavior = ReceiveBuilder.create()
                .match(Acknowledgement.class, this::forwardAcknowledgement)
                .match(Signal.class, this::handleSignal)
                .build();

        return addPreprocessors(List.of(stripEnvelope, setAckRequest, startAckregator), signalBehavior);
    }

    private Receive createOutgoingSignalBehavior() {
        return ReceiveBuilder.create()
                .match(Acknowledgement.class, ack ->
                        potentiallyForwardToAckregator(ack, () -> logger.withCorrelationId(ack)
                                .info("About to send Acknowledgement but no AcknowledgementAggregatorActor " +
                                        "was present: <{}>", ack))
                )
                .match(ThingCommandResponse.class, response ->
                        potentiallyForwardToAckregator(response, () -> handleResponse(response))
                )
                .match(CommandResponse.class, this::handleResponse)
                .match(Signal.class, AcknowledgementForwarderActor::shouldStartForOutgoing,
                        this::handleSignalsToStartAckForwarderFor)
                .match(Signal.class, this::handleSignal)
                .match(DittoRuntimeException.class, cre -> {
                    logger.withCorrelationId(cre)
                            .info("Got 'DittoRuntimeException' message in <{}> session, telling" +
                                    " EventAndResponsePublisher about it: {}", type, cre);
                    eventAndResponsePublisher.forward(SessionedJsonifiable.error(cre), getContext());
                })
                .build();
    }

    private Receive createPubSubBehavior() {
        return ReceiveBuilder.create()
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
                .match(AcknowledgeSubscription.class, msg ->
                        acknowledgeSubscription(msg.getStreamingType(), getSelf()))
                .match(AcknowledgeUnsubscription.class, msg ->
                        acknowledgeUnsubscription(msg.getStreamingType(), getSelf()))
                .build();
    }

    private Receive createSelfTerminationBehavior() {
        return ReceiveBuilder.create()
                .match(RefreshSession.class, refreshSession -> {
                    cancelSessionTimeout();
                    checkAuthorizationContextAndStartSessionTimer(refreshSession);
                })
                .match(InvalidJwt.class, invalidJwtToken -> cancelSessionTimeout())
                .match(Terminated.class, terminated -> {
                    logger.setCorrelationId(connectionCorrelationId);
                    logger.debug("EventAndResponsePublisher was terminated.");
                    // In Cluster: Unsubscribe from ThingEvents:
                    logger.info("<{}> connection was closed, unsubscribing from Streams in Cluster ...", type);

                    dittoProtocolSub.removeSubscriber(getSelf());

                    // TODO: convert to timer
                    getContext()
                            .getSystem()
                            .scheduler()
                            .scheduleOnce(FiniteDuration.apply(1, TimeUnit.SECONDS), getSelf(),
                                    PoisonPill.getInstance(), getContext().dispatcher(), getSelf());
                })
                .build();
    }

    private Receive logUnknownMessage(final Function<Object, Object> logMapper) {
        return ReceiveBuilder.create()
                .matchAny(any -> logger.withCorrelationId(connectionCorrelationId)
                        .warning("Got unknown message in '{}' session: '{}'", type, logMapper.apply(any)))
                .build();
    }

    private Receive addPreprocessors(final List<PartialFunction<Object, Object>> preprocessors, final Receive receive) {
        return preprocessors.stream()
                .reduce(PartialFunction::andThen)
                .map(preprocessor -> new Receive(preprocessor.andThen(receive.onMessage())))
                .orElse(receive);
    }

    private boolean isLiveSignal(final WithDittoHeaders<?> signal) {
        return signal.getDittoHeaders()
                .getChannel()
                .filter(value -> TopicPath.Channel.LIVE.getName().equals(value))
                .isPresent();
    }

    private boolean isResponseRequired(final WithDittoHeaders<?> signal) {
        return signal.getDittoHeaders().isResponseRequired();
    }

    private void potentiallyForwardToAckregator(final CommandResponse<?> response, final Runnable fallbackAction) {
        final ActorContext context = getContext();
        final Consumer<ActorRef> action = aggregator -> aggregator.forward(response, context);

        context.findChild(
                AcknowledgementAggregatorActor.determineActorName(response.getDittoHeaders())
        ).ifPresentOrElse(action, fallbackAction);
    }

    private Object startAckregator(final Signal<?> signal) {
        try {
            AcknowledgementAggregatorActor.startAcknowledgementAggregator(getContext(),
                    signal,
                    acknowledgementConfig, headerTranslator,
                    response -> handleResponseThreadSafely(response, ActorRef.noSender()));
        } catch (final DittoRuntimeException e) {
            logger.withCorrelationId(signal).info(START_ACK_AGGREGATOR_ERROR_MSG_TEMPLATE, type,
                    e.getClass().getSimpleName(), e.getMessage());
            eventAndResponsePublisher.tell(SessionedJsonifiable.error(e), getSelf());
        }
        return signal;
    }

    // NOT thread-safe
    private void handleResponse(final CommandResponse<?> response) {
        handleResponseThreadSafely(response, getSender());
    }

    private void handleResponseThreadSafely(final Object responseOrError, @Nullable final ActorRef sender) {
        if (responseOrError instanceof CommandResponse<?>) {
            final CommandResponse<?> response = (CommandResponse<?>) responseOrError;
            logger.withCorrelationId(response)
                    .debug("Got 'CommandResponse' message in <{}> session, telling EventAndResponsePublisher" +
                            " about it: {}", type, response);
            eventAndResponsePublisher.tell(SessionedJsonifiable.response(response), sender);
        } else if (responseOrError instanceof DittoRuntimeException) {
            final DittoRuntimeException error = (DittoRuntimeException) responseOrError;
            logger.withCorrelationId(error)
                    .debug("Got 'DittoRuntimeException' message in <{}> session, telling EventAndResponsePublisher" +
                            " about it: {}", type, error);
            eventAndResponsePublisher.tell(SessionedJsonifiable.error(error), sender);
        } else {
            logger.error("Unexpected result from AcknowledgementAggregatorActor: <{}>", responseOrError);
        }
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

    // precondition: signal instanceof WithThingId
    // guaranteed by AcknowledgementForwarderActor::hasEffectiveAckRequests
    private void handleSignalsToStartAckForwarderFor(final Signal<?> signal) {
        final EntityIdWithType entityIdWithType = ((WithThingId) signal).getThingEntityId();
        AcknowledgementForwarderActor.startAcknowledgementForwarder(getContext(), entityIdWithType, signal,
                acknowledgementConfig);
        handleSignal(signal);
    }

    // TODO: split directions
    private void handleSignal(final Signal<?> signal) {
        logger.setCorrelationId(signal);
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        if (signal instanceof CreateSubscription || signal instanceof RequestFromSubscription ||
                signal instanceof CancelSubscription) {
            subscriptionManager.tell(signal, getSelf());
        } else if (signal instanceof SubscriptionEvent) {
            logger.debug("Got SubscriptionEvent <{}> in <{}> session, telling EventAndResponsePublisher about it: {}",
                    signal.getType(), type, signal);
            eventAndResponsePublisher.tell(SessionedJsonifiable.subscription((SubscriptionEvent<?>) signal), getSelf());
        } else if (signal instanceof CommandResponse) {
            logger.debug("Got CommandResponse <{}> in <{}> session, telling EventAndResponsePublisher about it: {}",
                    signal.getType(), type, signal);
            eventAndResponsePublisher.forward(SessionedJsonifiable.response((CommandResponse<?>) signal), getContext());
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
                eventAndResponsePublisher.tell(sessionedJsonifiable, getSelf());
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
            eventAndResponsePublisher.tell(CloseStreamExceptionally.getInstance(gatewayWebsocketSessionClosedException,
                    connectionCorrelationId), getSelf());
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
