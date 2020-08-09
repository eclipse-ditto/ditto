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

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.jwt.ImmutableJsonWebToken;
import org.eclipse.ditto.model.jwt.JsonWebToken;
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
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.services.gateway.streaming.CloseStreamExceptionally;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.IncomingSignal;
import org.eclipse.ditto.services.gateway.streaming.InvalidJwt;
import org.eclipse.ditto.services.gateway.streaming.Jwt;
import org.eclipse.ditto.services.gateway.streaming.RefreshSession;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.models.acks.AcknowledgementAggregatorActorStarter;
import org.eclipse.ditto.services.models.acks.AcknowledgementForwarderActor;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.search.SubscriptionManager;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayWebsocketSessionClosedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayWebsocketSessionExpiredException;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.acks.MessageCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.acks.ThingLiveCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.acks.ThingModifyCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;

import akka.Done;
import akka.actor.AbstractActorWithTimers;
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
final class StreamingSessionActor extends AbstractActorWithTimers {

    private final JsonSchemaVersion jsonSchemaVersion;
    private final String connectionCorrelationId;
    private final String type;
    private final DittoProtocolSub dittoProtocolSub;
    private final ActorRef eventAndResponsePublisher;
    private final ActorRef commandRouter;
    private final AcknowledgementConfig acknowledgementConfig;
    private final ActorRef subscriptionManager;
    private final Set<StreamingType> outstandingSubscriptionAcks;
    private final Map<StreamingType, StreamingSession> streamingSessions;
    private final JwtValidator jwtValidator;
    private final JwtAuthenticationResultProvider jwtAuthenticationResultProvider;
    private final AcknowledgementAggregatorActorStarter ackregatorStarter;
    private final DittoDiagnosticLoggingAdapter logger;

    @Nullable private Cancellable sessionTerminationCancellable;
    private AuthorizationContext authorizationContext;

    @SuppressWarnings("unused")
    private StreamingSessionActor(final Connect connect,
            final DittoProtocolSub dittoProtocolSub,
            final ActorRef eventAndResponsePublisher,
            final ActorRef commandRouter,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Props subscriptionManagerProps,
            final JwtValidator jwtValidator,
            final JwtAuthenticationResultProvider jwtAuthenticationResultProvider) {

        jsonSchemaVersion = connect.getJsonSchemaVersion();
        connectionCorrelationId = connect.getConnectionCorrelationId();
        type = connect.getType();
        this.dittoProtocolSub = dittoProtocolSub;
        this.eventAndResponsePublisher = eventAndResponsePublisher;
        this.commandRouter = commandRouter;
        this.acknowledgementConfig = acknowledgementConfig;
        this.jwtValidator = jwtValidator;
        this.jwtAuthenticationResultProvider = jwtAuthenticationResultProvider;
        outstandingSubscriptionAcks = EnumSet.noneOf(StreamingType.class);
        authorizationContext = AuthorizationModelFactory.emptyAuthContext();
        streamingSessions = new EnumMap<>(StreamingType.class);
        ackregatorStarter = AcknowledgementAggregatorActorStarter.of(getContext(),
                acknowledgementConfig,
                headerTranslator,
                ThingModifyCommandAckRequestSetter.getInstance(),
                ThingLiveCommandAckRequestSetter.getInstance(),
                MessageCommandAckRequestSetter.getInstance());
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
     * @param eventAndResponsePublisher the {@link org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher} actor.
     * @param commandRouter the actor who distributes incoming commands in the Ditto cluster.
     * @param acknowledgementConfig the config to apply for Acknowledgements.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param subscriptionManagerProps Props of the subscription manager for search protocol.
     * @param jwtValidator validator of JWT tokens.
     * @param jwtAuthenticationResultProvider provider of JWT authentication results.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connect connect,
            final DittoProtocolSub dittoProtocolSub,
            final ActorRef eventAndResponsePublisher,
            final ActorRef commandRouter,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Props subscriptionManagerProps,
            final JwtValidator jwtValidator,
            final JwtAuthenticationResultProvider jwtAuthenticationResultProvider) {

        return Props.create(StreamingSessionActor.class, connect, dittoProtocolSub, eventAndResponsePublisher,
                commandRouter, acknowledgementConfig, headerTranslator, subscriptionManagerProps, jwtValidator,
                jwtAuthenticationResultProvider);
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
                .orElse(logUnknownMessage());
    }

    private Receive createIncomingSignalBehavior() {
        final PartialFunction<Object, Object> stripEnvelope = new PFBuilder<>()
                .match(IncomingSignal.class, IncomingSignal::getSignal)
                .build();

        final PartialFunction<Object, Object> setAckRequestAndStartAckregator = new PFBuilder<>()
                .match(Signal.class, this::startAckregatorAndForward)
                .matchAny(x -> x)
                .build();

        final Receive signalBehavior = ReceiveBuilder.create()
                .match(CommandResponse.class, this::forwardAcknowledgementOrLiveCommandResponse)
                .match(ThingSearchCommand.class, this::forwardSearchCommand)
                .match(Signal.class, signal ->
                        // forward signals for which no reply is expected with self return address for downstream errors
                        commandRouter.tell(signal, getSelf()))
                .matchEquals(Done.getInstance(), done -> {})
                .build();

        return addPreprocessors(List.of(stripEnvelope, setAckRequestAndStartAckregator), signalBehavior);
    }

    private Receive createOutgoingSignalBehavior() {
        final PartialFunction<Object, Object> setCorrelationIdAndStartAckForwarder = new PFBuilder<>()
                .match(Signal.class, signal -> {
                    logger.setCorrelationId(signal);
                    return startAckForwarder(signal);
                })
                .match(DittoRuntimeException.class, x -> x)
                .build();

        final Receive publishSignal = ReceiveBuilder.create()
                .match(SubscriptionEvent.class, signal -> {
                    logger.debug("Got SubscriptionEvent in <{}> session, publishing: {}", type, signal);
                    eventAndResponsePublisher.tell(SessionedJsonifiable.subscription(signal), getSelf());
                })
                .match(CommandResponse.class, this::publishResponseOrError)
                .match(DittoRuntimeException.class, this::publishResponseOrError)
                .match(Signal.class, this::isSameOrigin, signal ->
                        logger.debug("Got Signal <{}> in <{}> session, but this was issued by " +
                                " this connection itself, not publishing", signal.getType(), type)
                )
                .match(Signal.class, signal -> {
                    // check if this session is "allowed" to receive the Signal
                    @Nullable final StreamingSession session = streamingSessions.get(determineStreamingType(signal));
                    if (null != session && isSessionAllowedToReceiveSignal(signal, session)) {
                        logger.debug("Got Signal in <{}> session, publishing: {}", type, signal);

                        final DittoHeaders sessionHeaders = DittoHeaders.newBuilder()
                                .authorizationContext(authorizationContext)
                                .schemaVersion(jsonSchemaVersion)
                                .build();
                        final SessionedJsonifiable sessionedJsonifiable =
                                SessionedJsonifiable.signal(signal, sessionHeaders, session);
                        eventAndResponsePublisher.tell(sessionedJsonifiable, getSelf());
                    }
                })
                .matchEquals(Done.getInstance(), done -> { /* already done, nothing to publish */ })
                .build();

        return addPreprocessors(List.of(setCorrelationIdAndStartAckForwarder), publishSignal);
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
                    final ConfirmSubscription subscribeConfirmation =
                            new ConfirmSubscription(startStreaming.getStreamingType());
                    final Collection<StreamingType> currentStreamingTypes = streamingSessions.keySet();
                    dittoProtocolSub.subscribe(currentStreamingTypes, authorizationContext.getAuthorizationSubjectIds(),
                            getSelf()).thenAccept(ack -> getSelf().tell(subscribeConfirmation, getSelf()));
                })
                .match(StopStreaming.class, stopStreaming -> {
                    logger.debug("Got 'StopStreaming' message in <{}> session, unsubscribing from <{}> in Cluster ...",
                            type, stopStreaming.getStreamingType().name());

                    streamingSessions.remove(stopStreaming.getStreamingType());

                    // In Cluster: Unsubscribe
                    final ConfirmUnsubscription unsubscribeConfirmation =
                            new ConfirmUnsubscription(stopStreaming.getStreamingType());
                    final Collection<StreamingType> currentStreamingTypes = streamingSessions.keySet();
                    if (stopStreaming.getStreamingType() != StreamingType.EVENTS) {
                        dittoProtocolSub.updateLiveSubscriptions(currentStreamingTypes,
                                authorizationContext.getAuthorizationSubjectIds(), getSelf())
                                .thenAccept(ack -> getSelf().tell(unsubscribeConfirmation, getSelf()));
                    } else {
                        dittoProtocolSub.removeTwinSubscriber(getSelf(),
                                authorizationContext.getAuthorizationSubjectIds())
                                .thenAccept(ack -> getSelf().tell(unsubscribeConfirmation, getSelf()));
                    }
                })
                .match(ConfirmSubscription.class, msg -> confirmSubscription(msg.getStreamingType(), getSelf()))
                .match(ConfirmUnsubscription.class, msg -> confirmUnsubscription(msg.getStreamingType(), getSelf()))
                .build();
    }

    private Receive createSelfTerminationBehavior() {
        return ReceiveBuilder.create()
                .match(Jwt.class, this::refreshWebSocketSession)
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

                    final PoisonPill poisonPill = PoisonPill.getInstance();
                    getTimers().startSingleTimer(poisonPill, poisonPill, Duration.ofSeconds(1L));
                })
                .build();
    }

    private Receive logUnknownMessage() {
        return ReceiveBuilder.create()
                .matchAny(any -> logger.withCorrelationId(connectionCorrelationId)
                        .warning("Got unknown message in '{}' session: '{}'", type, any))
                .build();
    }

    private Receive addPreprocessors(final List<PartialFunction<Object, Object>> preprocessors, final Receive receive) {
        return preprocessors.stream()
                .reduce(PartialFunction::andThen)
                .map(preprocessor -> new Receive(preprocessor.andThen(receive.onMessage())))
                .orElse(receive);
    }

    private boolean isSameOrigin(final Signal<?> signal) {
        return signal.getDittoHeaders().getOrigin().stream().anyMatch(connectionCorrelationId::equals);
    }

    // precondition: signal has ack requests
    private Object startAckregatorAndForward(final Signal<?> signal) {
        return ackregatorStarter.start(signal,
                this::publishResponseWithoutSender,
                this::forwardToCommandRouterAndReturnDone,
                this::doNothing);
    }

    private Object forwardToCommandRouterAndReturnDone(final Signal<?> signalToForward, final ActorRef ackregator) {
        commandRouter.tell(signalToForward, ackregator);
        return Done.getInstance();
    }

    private <T> Object doNothing(final T result) {
        return result;
    }

    // no precondition; forwarder starter does not start for signals without ack requests, in contrast to ackregator
    private Signal<?> startAckForwarder(final Signal<?> signal) {
        if (signal instanceof WithThingId) {
            final EntityIdWithType entityIdWithType = ((WithThingId) signal).getThingEntityId();
            return AcknowledgementForwarderActor.startAcknowledgementForwarderConflictFree(getContext(),
                    entityIdWithType, signal, acknowledgementConfig);
        } else {
            return signal;
        }
    }

    // NOT thread-safe
    private void publishResponseOrError(final Object responseOrError) {
        publishResponseThreadSafely(responseOrError, getSender());
    }

    private void publishResponseWithoutSender(final Object responseOrError) {
        publishResponseThreadSafely(responseOrError, ActorRef.noSender());
    }

    private void publishResponseThreadSafely(final Object responseOrError, @Nullable final ActorRef sender) {
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

    private void forwardAcknowledgementOrLiveCommandResponse(final CommandResponse<?> response) {
        // the Acknowledgement / live CommandResponse is meant for someone else:
        final ActorContext context = getContext();
        context.findChild(AcknowledgementForwarderActor.determineActorName(response.getDittoHeaders()))
                .ifPresentOrElse(
                        forwarder -> forwarder.forward(response, context),
                        () -> logger.withCorrelationId(response)
                                .info("Received Acknowledgement / live CommandResponse but no " +
                                        "AcknowledgementForwarderActor was present: <{}>", response)
                );
    }

    private void forwardSearchCommand(final ThingSearchCommand<?> searchCommand) {
        subscriptionManager.tell(searchCommand, getSelf());
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

    private void refreshWebSocketSession(final Jwt jwt) {
        final String jwtConnectionCorrelationId = jwt.getConnectionCorrelationId();
        final JsonWebToken jsonWebToken = ImmutableJsonWebToken.fromToken(jwt.toString());
        jwtValidator.validate(jsonWebToken).thenAccept(binaryValidationResult -> {
            if (binaryValidationResult.isValid()) {
                try {
                    final AuthenticationResult authorizationResult =
                            jwtAuthenticationResultProvider.getAuthenticationResult(jsonWebToken, DittoHeaders.empty());
                    final AuthorizationContext jwtAuthorizationContext =
                            authorizationResult.getDittoHeaders().getAuthorizationContext();

                    getSelf().tell(new RefreshSession(jwtConnectionCorrelationId, jsonWebToken.getExpirationTime(),
                            jwtAuthorizationContext), ActorRef.noSender());
                } catch (final Exception exception) {
                    logger.info("Got exception when handling refreshed JWT for WebSocket session <{}>: {}",
                            jwtConnectionCorrelationId, exception.getMessage());
                    getSelf().tell(InvalidJwt.getInstance(), ActorRef.noSender());
                }
            } else {
                getSelf().tell(InvalidJwt.getInstance(), ActorRef.noSender());
            }
        });
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

    private void confirmSubscription(final StreamingType streamingType, final ActorRef self) {
        if (outstandingSubscriptionAcks.contains(streamingType)) {
            outstandingSubscriptionAcks.remove(streamingType);
            eventAndResponsePublisher.tell(SessionedJsonifiable.ack(streamingType, true, connectionCorrelationId),
                    self);
            logger.debug("Subscribed to Cluster <{}> in <{}> session.", streamingType, type);
        } else {
            logger.debug("Subscription already acked for type <{}> in <{}> session.", streamingType, type);
        }
    }

    private void confirmUnsubscription(final StreamingType streamingType, final ActorRef self) {
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

    private static final class ConfirmSubscription extends WithStreamingType {

        private ConfirmSubscription(final StreamingType streamingType) {
            super(streamingType);
        }

    }

    private static final class ConfirmUnsubscription extends WithStreamingType {

        private ConfirmUnsubscription(final StreamingType streamingType) {
            super(streamingType);
        }

    }

}
