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
package org.eclipse.ditto.gateway.service.streaming.actors;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotDeclaredException;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.base.model.acks.FatalPubSubException;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.namespaces.NamespaceReader;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.edge.api.placeholders.EntityIdPlaceholder;
import org.eclipse.ditto.gateway.api.GatewayInternalErrorException;
import org.eclipse.ditto.gateway.api.GatewayWebsocketSessionClosedException;
import org.eclipse.ditto.gateway.api.GatewayWebsocketSessionExpiredException;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.gateway.service.streaming.Connect;
import org.eclipse.ditto.gateway.service.streaming.IncomingSignal;
import org.eclipse.ditto.gateway.service.streaming.InvalidJwt;
import org.eclipse.ditto.gateway.service.streaming.Jwt;
import org.eclipse.ditto.gateway.service.streaming.RefreshSession;
import org.eclipse.ditto.gateway.service.streaming.StartStreaming;
import org.eclipse.ditto.gateway.service.streaming.StopStreaming;
import org.eclipse.ditto.internal.models.acks.AcknowledgementAggregatorActorStarter;
import org.eclipse.ditto.internal.models.acks.AcknowledgementForwarderActor;
import org.eclipse.ditto.internal.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.pubsub.DittoProtocolSub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.search.SubscriptionManager;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.acks.MessageCommandAckRequestSetter;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.protocol.placeholders.ResourcePlaceholder;
import org.eclipse.ditto.protocol.placeholders.TopicPathPlaceholder;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.things.model.signals.commands.acks.ThingLiveCommandAckRequestSetter;
import org.eclipse.ditto.things.model.signals.commands.acks.ThingModifyCommandAckRequestSetter;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;

import akka.Done;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.SourceQueueWithComplete;
import scala.PartialFunction;

/**
 * Actor handling a single streaming connection / session.
 */
final class StreamingSessionActor extends AbstractActorWithTimers {

    /**
     * Maximum lifetime of an expiring session.
     * If a session is established with JWT lasting more than this duration, the session will persist forever.
     */
    private static final Duration MAX_SESSION_TIMEOUT = Duration.ofDays(100L);

    private final JsonSchemaVersion jsonSchemaVersion;
    private final String connectionCorrelationId;
    private final String type;
    private final DittoProtocolSub dittoProtocolSub;
    private final SourceQueueWithComplete<SessionedJsonifiable> eventAndResponsePublisher;
    private final ActorRef commandRouter;
    private final AcknowledgementConfig acknowledgementConfig;
    private final ActorRef subscriptionManager;
    private final Set<StreamingType> outstandingSubscriptionAcks;
    private final Map<StreamingType, StreamingSession> streamingSessions;
    private final JwtValidator jwtValidator;
    private final JwtAuthenticationResultProvider jwtAuthenticationResultProvider;
    private final AcknowledgementAggregatorActorStarter ackregatorStarter;
    private final Set<AcknowledgementLabel> declaredAcks;
    private final ThreadSafeDittoLoggingAdapter logger;

    private AuthorizationContext authorizationContext;

    @SuppressWarnings("unused")
    private StreamingSessionActor(final Connect connect,
            final DittoProtocolSub dittoProtocolSub,
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
        eventAndResponsePublisher = connect.getEventAndResponsePublisher();
        this.commandRouter = commandRouter;
        this.acknowledgementConfig = acknowledgementConfig;
        this.jwtValidator = jwtValidator;
        this.jwtAuthenticationResultProvider = jwtAuthenticationResultProvider;
        outstandingSubscriptionAcks = EnumSet.noneOf(StreamingType.class);
        authorizationContext = connect.getConnectionAuthContext();
        streamingSessions = new EnumMap<>(StreamingType.class);
        ackregatorStarter = AcknowledgementAggregatorActorStarter.of(getContext(),
                acknowledgementConfig,
                headerTranslator,
                null,
                ThingModifyCommandAckRequestSetter.getInstance(),
                ThingLiveCommandAckRequestSetter.getInstance(),
                MessageCommandAckRequestSetter.getInstance());
        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this)
                .withCorrelationId(connectionCorrelationId);
        connect.getSessionExpirationTime().ifPresent(this::startSessionTimeout);
        subscriptionManager = getContext().actorOf(subscriptionManagerProps, SubscriptionManager.ACTOR_NAME);
        declaredAcks = connect.getDeclaredAcknowledgementLabels();
    }

    /**
     * Creates Akka configuration object Props for this StreamingSessionActor.
     *
     * @param connect the command to start a streaming session.
     * @param dittoProtocolSub manager of subscriptions.
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
            final ActorRef commandRouter,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Props subscriptionManagerProps,
            final JwtValidator jwtValidator,
            final JwtAuthenticationResultProvider jwtAuthenticationResultProvider) {

        return Props.create(StreamingSessionActor.class,
                connect,
                dittoProtocolSub,
                commandRouter,
                acknowledgementConfig,
                headerTranslator,
                subscriptionManagerProps,
                jwtValidator,
                jwtAuthenticationResultProvider);
    }

    @Override
    public void preStart() {
        eventAndResponsePublisher.watchCompletion()
                .whenComplete((done, error) -> getSelf().tell(Control.TERMINATED, getSelf()));
        declareAcknowledgementLabels(declaredAcks);
    }

    @Override
    public void postStop() {
        logger.info("Closing <{}> streaming session.", type);
        cancelSessionTimeout();
        eventAndResponsePublisher.complete();
    }

    @Override
    public Receive createReceive() {
        return createIncomingSignalBehavior()
                .orElse(createPubSubBehavior())
                .orElse(createSelfTerminationBehavior())
                .orElse(createOutgoingSignalBehavior())
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
                .match(Acknowledgement.class, this::hasUndeclaredAckLabel, this::ackLabelNotDeclared)
                .match(CommandResponse.class, this::forwardAcknowledgementOrLiveCommandResponse)
                .match(ThingSearchCommand.class, this::forwardSearchCommand)
                .match(Signal.class, signal ->
                        // forward signals for which no reply is expected with self return address for downstream errors
                        commandRouter.tell(signal, getReturnAddress(signal)))
                .matchEquals(Done.getInstance(), done -> {})
                .build();

        return addPreprocessors(List.of(stripEnvelope, setAckRequestAndStartAckregator), signalBehavior);
    }

    private Receive createOutgoingSignalBehavior() {
        final PartialFunction<Object, Object> setCorrelationIdAndStartAckForwarder = new PFBuilder<>()
                .match(Signal.class, this::startAckForwarder)
                .match(DittoRuntimeException.class, x -> x)
                .build();

        final Receive publishSignal = ReceiveBuilder.create()
                .match(SubscriptionEvent.class, signal -> {
                    logger.debug("Got SubscriptionEvent in <{}> session, publishing: {}", type, signal);
                    eventAndResponsePublisher.offer(SessionedJsonifiable.subscription(signal));
                })
                .match(CommandResponse.class, this::publishResponseOrError)
                .match(DittoRuntimeException.class, this::publishResponseOrError)
                .match(Signal.class, this::isSameOrigin, signal ->
                        logger.withCorrelationId(signal)
                                .debug("Got Signal of type <{}> in <{}> session, but this was issued by " +
                                        " this connection itself, not publishing", signal.getType(), type)
                )
                .match(Signal.class, signal -> {
                    // check if this session is "allowed" to receive the Signal
                    final var streamingType = determineStreamingType(signal);
                    @Nullable final var session = streamingSessions.get(streamingType);
                    if (null != session && isSessionAllowedToReceiveSignal(signal, session, streamingType)) {
                        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(signal);
                        l.info("Publishing Signal of type <{}> in <{}> session", signal.getType(), type);
                        l.debug("Publishing Signal of type <{}> in <{}> session: {}", type, signal.getType(), signal);

                        final DittoHeaders sessionHeaders = DittoHeaders.newBuilder()
                                .authorizationContext(authorizationContext)
                                .schemaVersion(jsonSchemaVersion)
                                .build();
                        final var sessionedJsonifiable =
                                SessionedJsonifiable.signal(signal, sessionHeaders, session);
                        eventAndResponsePublisher.offer(sessionedJsonifiable);
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
                    Criteria criteria;
                    try {
                        criteria = startStreaming.getFilter()
                                .map(f -> parseCriteria(f, DittoHeaders.newBuilder()
                                        .correlationId(startStreaming.getCorrelationId()
                                                .orElse(startStreaming.getConnectionCorrelationId()))
                                        .build()))
                                .orElse(null);
                    } catch (final DittoRuntimeException e) {
                        logger.info("Got 'DittoRuntimeException' <{}> session during 'StartStreaming' processing:" +
                                " {}: <{}>", type, e.getClass().getSimpleName(), e.getMessage());
                        eventAndResponsePublisher.offer(SessionedJsonifiable.error(e));
                        return;
                    }
                    final var session = StreamingSession.of(startStreaming.getNamespaces(), criteria,
                            startStreaming.getExtraFields().orElse(null), getSelf(), logger);
                    streamingSessions.put(startStreaming.getStreamingType(), session);

                    logger.debug("Got 'StartStreaming' message in <{}> session, subscribing for <{}> in Cluster ...",
                            type, startStreaming.getStreamingType().name());

                    outstandingSubscriptionAcks.add(startStreaming.getStreamingType());
                    // In Cluster: Subscribe
                    final var subscribeConfirmation = new ConfirmSubscription(startStreaming.getStreamingType());
                    final Collection<StreamingType> currentStreamingTypes = streamingSessions.keySet();
                    dittoProtocolSub.subscribe(currentStreamingTypes,
                            authorizationContext.getAuthorizationSubjectIds(),
                            getSelf()
                    ).whenComplete((ack, throwable) -> {
                        if (null == throwable) {
                            logger.debug("subscription to Ditto pubsub succeeded");
                            getSelf().tell(subscribeConfirmation, getSelf());
                        } else {
                            logger.error(throwable, "subscription to Ditto pubsub failed: {}", throwable.getMessage());
                            final var dittoRuntimeException = DittoRuntimeException
                                    .asDittoRuntimeException(throwable,
                                            cause -> GatewayInternalErrorException.newBuilder()
                                                    .dittoHeaders(DittoHeaders.newBuilder()
                                                            .correlationId(startStreaming.getConnectionCorrelationId())
                                                            .build())
                                                    .cause(cause)
                                                    .build()
                                    );
                            eventAndResponsePublisher.offer(SessionedJsonifiable.error(dittoRuntimeException));
                            terminateWebsocketStream();
                        }
                    });
                })
                .match(StopStreaming.class, stopStreaming -> {
                    logger.debug("Got 'StopStreaming' message in <{}> session, unsubscribing from <{}> in Cluster ...",
                            type, stopStreaming.getStreamingType().name());

                    streamingSessions.remove(stopStreaming.getStreamingType());

                    // In Cluster: Unsubscribe
                    final var unsubscribeConfirmation = new ConfirmUnsubscription(stopStreaming.getStreamingType());
                    final Collection<StreamingType> currentStreamingTypes = streamingSessions.keySet();
                    switch (stopStreaming.getStreamingType()) {
                        case EVENTS:
                            dittoProtocolSub.removeTwinSubscriber(getSelf(),
                                            authorizationContext.getAuthorizationSubjectIds())
                                    .thenAccept(ack -> getSelf().tell(unsubscribeConfirmation, getSelf()));
                            break;
                        case POLICY_ANNOUNCEMENTS:
                            dittoProtocolSub.removePolicyAnnouncementSubscriber(getSelf(),
                                            authorizationContext.getAuthorizationSubjectIds())
                                    .thenAccept(ack -> getSelf().tell(unsubscribeConfirmation, getSelf()));
                            break;
                        case LIVE_COMMANDS:
                        case LIVE_EVENTS:
                        case MESSAGES:
                        default:
                            dittoProtocolSub.updateLiveSubscriptions(currentStreamingTypes,
                                            authorizationContext.getAuthorizationSubjectIds(), getSelf())
                                    .thenAccept(ack -> getSelf().tell(unsubscribeConfirmation, getSelf()));
                    }
                })
                .match(ConfirmSubscription.class, msg -> confirmSubscription(msg.getStreamingType()))
                .match(ConfirmUnsubscription.class, msg -> confirmUnsubscription(msg.getStreamingType()))
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
                .match(FatalPubSubException.class, this::pubsubFailed)
                .match(Terminated.class, this::handleTerminated)
                .matchEquals(Control.TERMINATED, this::handleTerminated)
                .matchEquals(Control.SESSION_TERMINATION, this::handleSessionTermination)
                .build();
    }

    private void handleTerminated(final Object terminated) {
        logger.debug("EventAndResponsePublisher was terminated: {}", terminated);
        // In Cluster: Unsubscribe from ThingEvents:
        logger.info("<{}> connection was closed, unsubscribing from Streams in Cluster ...", type);

        terminateWebsocketStream();
    }

    private Receive logUnknownMessage() {
        return ReceiveBuilder.create()
                .matchAny(any -> logger
                        .warning("Got unknown message in '{}' session: {} '{}'", type, any.getClass().getName(), any))
                .build();
    }

    private static Receive addPreprocessors(final List<PartialFunction<Object, Object>> preprocessors,
            final Receive receive) {

        return preprocessors.stream()
                .reduce(PartialFunction::andThen)
                .map(preprocessor -> new Receive(preprocessor.andThen(receive.onMessage())))
                .orElse(receive);
    }

    private boolean hasUndeclaredAckLabel(final Acknowledgement acknowledgement) {
        return !declaredAcks.contains(acknowledgement.getLabel());
    }

    private void ackLabelNotDeclared(final Acknowledgement ack) {
        publishResponseOrError(AcknowledgementLabelNotDeclaredException.of(ack.getLabel(), ack.getDittoHeaders()));
    }

    private ActorRef getReturnAddress(final Signal<?> signal) {
        final var publishResponse =
                Command.isCommand(signal) && signal.getDittoHeaders().isResponseRequired();
        return publishResponse ? getSelf() : ActorRef.noSender();
    }

    private boolean isSameOrigin(final Signal<?> signal) {
        return signal.getDittoHeaders().getOrigin().stream().anyMatch(connectionCorrelationId::equals);
    }

    private void pubsubFailed(final FatalPubSubException fatalPubSubException) {
        final var exception = fatalPubSubException.asDittoRuntimeException();
        logger.withCorrelationId(exception).info("pubsubFailed cause=<{}>", exception);
        eventAndResponsePublisher.offer(SessionedJsonifiable.error(exception));
        terminateWebsocketStream();
    }

    private void terminateWebsocketStream() {
        dittoProtocolSub.removeSubscriber(getSelf());
        getContext().stop(getSelf());
    }

    // precondition: signal has ack requests
    private Object startAckregatorAndForward(final Signal<?> signal) {
        return ackregatorStarter.preprocess(signal,
                (s, shouldStart) -> {
                    final var entityIdOptional = WithEntityId.getEntityId(s);
                    if (shouldStart && entityIdOptional.isPresent()) {
                        // websocket-specific header check: acks requested with response-required=false are forbidden
                        final Optional<DittoHeaderInvalidException> headerInvalid = checkForAcksWithoutResponse(s);
                        return headerInvalid.map(this::publishResponseOrError)
                                .orElseGet(() -> ackregatorStarter.doStart(entityIdOptional.get(),
                                        s, null, this::publishResponseOrError,
                                        ackregator -> forwardToCommandRouterAndReturnDone(s, ackregator)));
                    } else {
                        return doNothing(s);
                    }
                },
                this::publishResponseOrError
        );
    }

    private Object forwardToCommandRouterAndReturnDone(final Signal<?> signalToForward, final ActorRef ackregator) {
        commandRouter.tell(signalToForward, ackregator);
        return Done.getInstance();
    }

    private static <T> Object doNothing(final T result) {
        return result;
    }

    // no precondition; forwarder starter does not start for signals without ack requests, in contrast to ackregator
    private Signal<?> startAckForwarder(final Signal<?> signal) {
        final var entityIdOptional = WithEntityId.getEntityId(signal);
        if (entityIdOptional.isPresent()) {
            final var entityIdWithType = entityIdOptional.get();
            return AcknowledgementForwarderActor.startAcknowledgementForwarder(getContext(),
                    self(),
                    sender(),
                    entityIdWithType,
                    signal,
                    acknowledgementConfig,
                    declaredAcks::contains);
        } else {
            return signal;
        }
    }

    private Object publishResponseOrError(final Object responseOrError) {
        if (responseOrError instanceof CommandResponse<?>) {
            final CommandResponse<?> response = (CommandResponse<?>) responseOrError;
            logger.withCorrelationId(response)
                    .debug("Got 'CommandResponse' message in <{}> session, telling EventAndResponsePublisher" +
                            " about it: {}", type, response);
            eventAndResponsePublisher.offer(SessionedJsonifiable.response(response));
        } else if (responseOrError instanceof DittoRuntimeException) {
            final DittoRuntimeException error = (DittoRuntimeException) responseOrError;
            logger.withCorrelationId(error)
                    .debug("Got 'DittoRuntimeException' message in <{}> session, telling EventAndResponsePublisher" +
                            " about it: {}", type, error);
            eventAndResponsePublisher.offer(SessionedJsonifiable.error(error));
        } else {
            logger.error("Unexpected result from AcknowledgementAggregatorActor: <{}>", responseOrError);
        }
        return Done.getInstance();
    }

    private void forwardAcknowledgementOrLiveCommandResponse(final CommandResponse<?> response) {
        final ActorRef sender = getSender();
        try {
            getContext().findChild(AcknowledgementForwarderActor.determineActorName(response.getDittoHeaders()))
                    .ifPresentOrElse(
                            forwarder -> forwarder.tell(response, sender),
                            () -> {
                                // the Acknowledgement / LiveCommandResponse is meant for someone else:
                                final var template =
                                        "No AcknowledgementForwarderActor found, forwarding to concierge: <{}>";
                                if (logger.isDebugEnabled()) {
                                    logger.withCorrelationId(response).debug(template, response);
                                } else {
                                    logger.withCorrelationId(response).info(template, response.getType());
                                }
                                commandRouter.tell(response, ActorRef.noSender());
                            }
                    );
        } catch (final DittoRuntimeException e) {
            // error encountered; publish it
            eventAndResponsePublisher.offer(SessionedJsonifiable.error(e));
        }
    }

    private void forwardSearchCommand(final ThingSearchCommand<?> searchCommand) {
        subscriptionManager.tell(searchCommand, getSelf());
    }

    private boolean isSessionAllowedToReceiveSignal(final Signal<?> signal,
            final StreamingSession session,
            final StreamingType streamingType) {

        if (streamingType == StreamingType.POLICY_ANNOUNCEMENTS) {
            // recipients of policy announcements are authorized because the affected subjects are the pubsub topics
            return true;
        } else {
            final var headers = signal.getDittoHeaders();
            final boolean isAuthorizedToRead = authorizationContext.isAuthorized(headers.getReadGrantedSubjects(),
                    headers.getReadRevokedSubjects());
            final boolean matchesNamespace = matchesNamespaces(signal, session);
            return isAuthorizedToRead && matchesNamespace;
        }
    }

    private void startSessionTimeout(final Instant sessionExpirationTime) {
        final var sessionTimeout = Duration.between(Instant.now(), sessionExpirationTime);
        if (sessionTimeout.isNegative() || sessionTimeout.isZero()) {
            logger.debug("Session expired already. Closing WS.");
            getSelf().tell(Control.SESSION_TERMINATION, ActorRef.noSender());
        } else if (sessionTimeout.minus(MAX_SESSION_TIMEOUT).isNegative()) {
            logger.debug("Starting session timeout - session will expire in {}", sessionTimeout);
            getTimers().startSingleTimer(Control.SESSION_TERMINATION, Control.SESSION_TERMINATION, sessionTimeout);
        } else {
            logger.warning("Session lifetime <{}> is more than the maximum <{}>. Keeping session open indefinitely.",
                    sessionTimeout, MAX_SESSION_TIMEOUT);
        }
    }

    private void handleSessionTermination(final Control sessionTermination) {
        logger.info("Stopping WebSocket session for connection with ID <{}>.", connectionCorrelationId);
        final var gatewayWebsocketSessionExpiredException =
                GatewayWebsocketSessionExpiredException.newBuilder()
                        .dittoHeaders(DittoHeaders.newBuilder()
                                .correlationId(connectionCorrelationId)
                                .build())
                        .build();
        eventAndResponsePublisher.fail(gatewayWebsocketSessionExpiredException);
    }

    private void cancelSessionTimeout() {
        getTimers().cancel(Control.SESSION_TERMINATION);
    }

    private void checkAuthorizationContextAndStartSessionTimer(final RefreshSession refreshSession) {
        final var newAuthorizationContext = refreshSession.getAuthorizationContext();
        if (!authorizationContext.equals(newAuthorizationContext)) {
            logger.debug("Authorization Context changed for WebSocket session <{}>. Terminating the session.",
                    connectionCorrelationId);
            final var gatewayWebsocketSessionClosedException =
                    GatewayWebsocketSessionClosedException.newBuilder()
                            .dittoHeaders(DittoHeaders.newBuilder()
                                    .correlationId(connectionCorrelationId)
                                    .build())
                            .build();
            eventAndResponsePublisher.fail(gatewayWebsocketSessionClosedException);
        } else {
            startSessionTimeout(refreshSession.getSessionTimeout());
        }
    }

    private boolean matchesNamespaces(final Signal<?> signal, final StreamingSession session) {
        final List<String> namespaces = session.getNamespaces();
        final boolean result = namespaces.isEmpty() || namespaces.contains(namespaceFromId(signal));
        if (!result) {
            logger.withCorrelationId(signal).debug("Signal does not match namespaces.");
        }
        return result;
    }

    private void refreshWebSocketSession(final Jwt jwt) {
        final String jwtConnectionCorrelationId = jwt.getConnectionCorrelationId();
        final var jsonWebToken = ImmutableJsonWebToken.fromToken(jwt.toString());
        jwtValidator.validate(jsonWebToken).thenAccept(binaryValidationResult -> {
            if (binaryValidationResult.isValid()) {
                jwtAuthenticationResultProvider.getAuthenticationResult(jsonWebToken, DittoHeaders.empty())
                        .thenAccept(authorizationResult -> {
                            final var jwtAuthorizationContext = authorizationResult.getAuthorizationContext();
                            getSelf().tell(new RefreshSession(jwtConnectionCorrelationId,
                                    jsonWebToken.getExpirationTime(), jwtAuthorizationContext), ActorRef.noSender());
                        })
                        .exceptionally(exception -> {
                            logger.info("Got exception when handling refreshed JWT for WebSocket session <{}>: {}",
                                    jwtConnectionCorrelationId, exception.getMessage());
                            getSelf().tell(InvalidJwt.getInstance(), ActorRef.noSender());
                            return null;
                        });
            } else {
                logger.debug("Received invalid JWT for WebSocket session <{}>. Terminating the session.",
                        connectionCorrelationId);
                final var gatewayWebsocketSessionClosedException =
                        GatewayWebsocketSessionClosedException.newBuilderForInvalidToken()
                                .dittoHeaders(DittoHeaders.newBuilder()
                                        .correlationId(connectionCorrelationId)
                                        .build())
                                .build();
                eventAndResponsePublisher.fail(gatewayWebsocketSessionClosedException);
                getSelf().tell(InvalidJwt.getInstance(), ActorRef.noSender());
            }
        });
    }

    /**
     * Attempt to declare the acknowledgement labels (they must be unique cluster wide).
     * Only need to be done once per actor.
     *
     * @param acknowledgementLabels the acknowledgement labels to declare.
     */
    private void declareAcknowledgementLabels(final Collection<AcknowledgementLabel> acknowledgementLabels) {
        final ActorRef self = getSelf();
        logger.info("Declaring acknowledgement labels <{}>", acknowledgementLabels);
        dittoProtocolSub.declareAcknowledgementLabels(acknowledgementLabels, self, null)
                .thenAccept(unused -> logger.info("Acknowledgement label declaration successful for labels: <{}>",
                        acknowledgementLabels))
                .exceptionally(error -> {
                    final var dittoRuntimeException =
                            DittoRuntimeException.asDittoRuntimeException(error,
                                    cause -> AcknowledgementLabelNotUniqueException.newBuilder().cause(cause).build());
                    logger.info("Acknowledgement label declaration failed for labels: <{}> - cause: {} {}",
                            acknowledgementLabels, error.getClass().getSimpleName(), error.getMessage());
                    self.tell(dittoRuntimeException, ActorRef.noSender());
                    return null;
                });
    }

    private static StreamingType determineStreamingType(final Signal<?> signal) {
        final StreamingType streamingType;
        if (signal instanceof Event) {
            streamingType = Signal.isChannelLive(signal)
                    ? StreamingType.LIVE_EVENTS
                    : StreamingType.EVENTS;
        } else if (MessageCommand.isMessageCommand(signal)) {
            streamingType = StreamingType.MESSAGES;
        } else if (signal instanceof PolicyAnnouncement) {
            streamingType = StreamingType.POLICY_ANNOUNCEMENTS;
        } else {
            streamingType = StreamingType.LIVE_COMMANDS;
        }
        return streamingType;
    }

    @Nullable
    private static String namespaceFromId(final Signal<?> signal) {
        return WithEntityId.getEntityId(signal)
                .flatMap(NamespaceReader::fromEntityId)
                .orElse(null);
    }

    private static Criteria parseCriteria(final String filter, final DittoHeaders dittoHeaders) {
        final var queryFilterCriteriaFactory = QueryFilterCriteriaFactory.modelBased(
                RqlPredicateParser.getInstance(),
                TopicPathPlaceholder.getInstance(),
                EntityIdPlaceholder.getInstance(),
                ResourcePlaceholder.getInstance(),
                TimePlaceholder.getInstance()
        );

        return queryFilterCriteriaFactory.filterCriteria(filter, dittoHeaders);
    }

    private void confirmSubscription(final StreamingType streamingType) {
        if (outstandingSubscriptionAcks.contains(streamingType)) {
            outstandingSubscriptionAcks.remove(streamingType);
            eventAndResponsePublisher.offer(SessionedJsonifiable.ack(streamingType, true, connectionCorrelationId));
            logger.debug("Subscribed to Cluster <{}> in <{}> session.", streamingType, type);
        } else {
            logger.debug("Subscription already acked for type <{}> in <{}> session.", streamingType, type);
        }
    }

    private void confirmUnsubscription(final StreamingType streamingType) {
        eventAndResponsePublisher.offer(SessionedJsonifiable.ack(streamingType, false, connectionCorrelationId));
        logger.debug("Unsubscribed from Cluster <{}> in <{}> session.", streamingType, type);
    }

    private static Optional<DittoHeaderInvalidException> checkForAcksWithoutResponse(final Signal<?> signal) {
        final var dittoHeaders = signal.getDittoHeaders();
        if (!dittoHeaders.isResponseRequired() && !dittoHeaders.getAcknowledgementRequests().isEmpty()) {
            final var message = String.format("For WebSocket, it is forbidden to request acknowledgements while " +
                            "'%s' is set to false.",
                    DittoHeaderDefinition.RESPONSE_REQUIRED.getKey());
            final var invalidHeaderKey = DittoHeaderDefinition.REQUESTED_ACKS.getKey();
            final var description = String.format("Please set '%s' to [] or '%s' to true.",
                    invalidHeaderKey,
                    DittoHeaderDefinition.RESPONSE_REQUIRED.getKey());
            return Optional.of(DittoHeaderInvalidException.newBuilder()
                    .withInvalidHeaderKey(invalidHeaderKey)
                    .message(message)
                    .description(description)
                    .dittoHeaders(signal.getDittoHeaders())
                    .build());
        } else {
            return Optional.empty();
        }
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

    private enum Control {
        TERMINATED,
        SESSION_TERMINATION
    }

}
