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
package org.eclipse.ditto.services.gateway.endpoints.routes.websocket;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessageType.START_SEND_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessageType.START_SEND_LIVE_COMMANDS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessageType.START_SEND_LIVE_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessageType.START_SEND_MESSAGES;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessageType.STOP_SEND_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessageType.STOP_SEND_LIVE_COMMANDS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessageType.STOP_SEND_LIVE_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessageType.STOP_SEND_MESSAGES;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.SignalEnrichmentFailedException;
import org.eclipse.ditto.model.base.exceptions.TooManyRequestsException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.jwt.ImmutableJsonWebToken;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.model.policies.PolicyException;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.thingsearch.ThingSearchException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.gateway.endpoints.utils.EventSniffer;
import org.eclipse.ditto.services.gateway.endpoints.utils.GatewaySignalEnrichmentProvider;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.StreamControlMessage;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.gateway.streaming.StreamingSessionIdentifier;
import org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher;
import org.eclipse.ditto.services.gateway.streaming.actors.SessionedJsonifiable;
import org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor;
import org.eclipse.ditto.services.gateway.util.config.streaming.StreamingConfig;
import org.eclipse.ditto.services.gateway.util.config.streaming.WebsocketConfig;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.LimitRateByRejection;
import org.eclipse.ditto.services.utils.akka.logging.AutoCloseableSlf4jLogger;
import org.eclipse.ditto.services.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayWebsocketSessionClosedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayWebsocketSessionExpiredException;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.thingsearch.SearchErrorResponse;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.model.ws.UpgradeToWebSocket;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.japi.pf.PFBuilder;
import akka.pattern.Patterns;
import akka.stream.Attributes;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SinkShape;
import akka.stream.UniformFanInShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * Builder for creating Akka HTTP routes for {@code /ws}.
 */
@NotThreadSafe
public final class WebSocketRoute implements WebSocketRouteBuilder {

    /**
     * The backend sends the protocol message above suffixed by ":ACK" when the subscription was created. E.g.: {@code
     * START-SEND-EVENTS:ACK}
     */
    private static final String PROTOCOL_CMD_ACK_SUFFIX = ":ACK";

    private static final String STREAMING_TYPE_WS = "WS";

    private static final String BEARER = "Bearer";

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(WebSocketRoute.class);

    private static final Duration CONFIG_ASK_TIMEOUT = Duration.ofSeconds(5L);

    private static final String STREAMING_MESSAGES = "streaming_messages";
    private static final String WS = "ws";
    private static final String DIRECTION = "direction";
    private static final String TYPE = "type";
    private static final Counter IN_COUNTER = DittoMetrics.counter(STREAMING_MESSAGES)
            .tag(TYPE, WS)
            .tag(DIRECTION, "in");
    private static final Counter OUT_COUNTER = DittoMetrics.counter(STREAMING_MESSAGES)
            .tag(TYPE, WS)
            .tag(DIRECTION, "out");
    private static final Counter DROPPED_COUNTER = DittoMetrics.counter(STREAMING_MESSAGES)
            .tag(TYPE, WS)
            .tag(DIRECTION, "dropped");

    private final ActorRef streamingActor;
    private final StreamingConfig streamingConfig;

    private EventSniffer<String> incomingMessageSniffer;
    private EventSniffer<String> outgoingMessageSniffer;
    private WebSocketAuthorizationEnforcer authorizationEnforcer;
    private WebSocketSupervisor webSocketSupervisor;
    @Nullable private GatewaySignalEnrichmentProvider signalEnrichmentProvider;
    private HeaderTranslator headerTranslator;

    private WebSocketRoute(final ActorRef streamingActor, final StreamingConfig streamingConfig) {

        this.streamingActor = checkNotNull(streamingActor, "streamingActor");
        this.streamingConfig = streamingConfig;

        final EventSniffer<String> noOpEventSniffer = EventSniffer.noOp();
        incomingMessageSniffer = noOpEventSniffer;
        outgoingMessageSniffer = noOpEventSniffer;
        authorizationEnforcer = new NoOpAuthorizationEnforcer();
        webSocketSupervisor = new NoOpWebSocketSupervisor();
        signalEnrichmentProvider = null;
        headerTranslator = HeaderTranslator.empty();
    }

    /**
     * Returns an instance of this class.
     *
     * @param streamingActor the {@link org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor} reference.
     * @param streamingConfig the streaming configuration.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static WebSocketRoute getInstance(final ActorRef streamingActor, final StreamingConfig streamingConfig) {
        return new WebSocketRoute(streamingActor, streamingConfig);
    }

    @Override
    public WebSocketRouteBuilder withIncomingEventSniffer(final EventSniffer<String> eventSniffer) {
        incomingMessageSniffer = checkNotNull(eventSniffer, "eventSniffer");
        return this;
    }

    @Override
    public WebSocketRouteBuilder withOutgoingEventSniffer(final EventSniffer<String> eventSniffer) {
        outgoingMessageSniffer = checkNotNull(eventSniffer, "eventSniffer");
        return this;
    }

    @Override
    public WebSocketRouteBuilder withAuthorizationEnforcer(final WebSocketAuthorizationEnforcer enforcer) {
        authorizationEnforcer = checkNotNull(enforcer, "enforcer");
        return this;
    }

    @Override
    public WebSocketRouteBuilder withWebSocketSupervisor(final WebSocketSupervisor webSocketSupervisor) {
        this.webSocketSupervisor = checkNotNull(webSocketSupervisor, "webSocketSupervisor");
        return this;
    }

    @Override
    public WebSocketRouteBuilder withSignalEnrichmentProvider(
            @Nullable final GatewaySignalEnrichmentProvider provider) {
        signalEnrichmentProvider = provider;
        return this;
    }

    @Override
    public WebSocketRouteBuilder withHeaderTranslator(final HeaderTranslator headerTranslator) {
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        return this;
    }

    /**
     * Builds the {@code /ws} route.
     *
     * @return the {@code /ws} route.
     */
    @Override
    public Route build(final JsonSchemaVersion version,
            final CharSequence correlationId,
            final DittoHeaders dittoHeaders,
            final ProtocolAdapter chosenProtocolAdapter) {

        return Directives.extractUpgradeToWebSocket(
                upgradeToWebSocketHeader -> Directives.extractRequest(
                        request -> {
                            authorizationEnforcer.checkAuthorization(dittoHeaders);
                            return Directives.completeWithFuture(
                                    createWebSocket(upgradeToWebSocketHeader, version, correlationId.toString(),
                                            dittoHeaders, chosenProtocolAdapter, request));
                        }));
    }

    private CompletionStage<WebsocketConfig> retrieveWebsocketConfig() {
        return Patterns.ask(streamingActor, StreamingActor.Control.RETRIEVE_WEBSOCKET_CONFIG, CONFIG_ASK_TIMEOUT)
                .thenApply(reply -> (WebsocketConfig) reply); // fail future with ClassCastException on type error
    }

    private CompletionStage<HttpResponse> createWebSocket(final UpgradeToWebSocket upgradeToWebSocket,
            final JsonSchemaVersion version,
            final String requestCorrelationId,
            final DittoHeaders dittoHeaders,
            final ProtocolAdapter adapter,
            final HttpRequest request) {

        @Nullable final SignalEnrichmentFacade signalEnrichmentFacade =
                signalEnrichmentProvider == null ? null : signalEnrichmentProvider.getFacade(request);

        final StreamingSessionIdentifier connectionCorrelationId =
                StreamingSessionIdentifier.of(requestCorrelationId, UUID.randomUUID().toString());

        final AuthorizationContext authContext = dittoHeaders.getAuthorizationContext();
        LOGGER.withCorrelationId(connectionCorrelationId)
                .info("Creating WebSocket for connection authContext: <{}>", authContext);

        return retrieveWebsocketConfig().thenApply(websocketConfig -> {
            final Flow<Message, DittoRuntimeException, NotUsed> incoming =
                    createIncoming(version, connectionCorrelationId, authContext, dittoHeaders, adapter, request,
                            websocketConfig);
            final Flow<DittoRuntimeException, Message, NotUsed> outgoing =
                    createOutgoing(version, connectionCorrelationId, dittoHeaders, adapter, request,
                            websocketConfig, signalEnrichmentFacade);

            return upgradeToWebSocket.handleMessagesWith(incoming.via(outgoing));
        });
    }

    /* Incoming flow:
     *
     * Websocket message with streamed content
     *                  +
     *                  | strictify+sniffer
     *                  v
     *                String
     *                  +
     *                  |
     *                  v                   bad cast/bad signal
     * Extract stream control or signal +---------------+
     *                  +                               |
     *                  |                               |
     *                  v                               |
     * Either<StreamControlMessage, Signal>             |
     *                  +                               |
     *                  |                               |
     *                  v            too many requests  |
     *             Rate limiter +---------------------->+
     *                  +                               |
     *                  |                               |
     *                  v                               v
     *            StreamingActor                DittoRuntimeException
     */
    @SuppressWarnings("unchecked")
    private Flow<Message, DittoRuntimeException, NotUsed> createIncoming(final JsonSchemaVersion version,
            final CharSequence connectionCorrelationId,
            final AuthorizationContext connectionAuthContext,
            final DittoHeaders dittoHeaders,
            final ProtocolAdapter adapter,
            final HttpRequest request,
            final WebsocketConfig websocketConfig) {

        return Flow.fromGraph(GraphDSL.create(builder -> {

            final FlowShape<Message, String> strictify =
                    builder.add(getStrictifyFlow(request, connectionCorrelationId)
                            .via(AbstractRoute.throttleByConfig(websocketConfig.getThrottlingConfig())));

            final FanOutShape2<String, Either<StreamControlMessage, Signal>, DittoRuntimeException> select =
                    builder.add(selectStreamControlOrSignal(version, connectionCorrelationId, connectionAuthContext,
                            dittoHeaders, adapter));

            final FanOutShape2<Either<StreamControlMessage, Signal>, Either<StreamControlMessage, Signal>,
                    DittoRuntimeException> rateLimiter = builder.add(getRateLimiter(websocketConfig));

            final FlowShape<DittoRuntimeException, DittoRuntimeException> droppedCounter =
                    builder.add(Flow.fromFunction(e -> {
                        DROPPED_COUNTER.increment();
                        return e;
                    }));

            final SinkShape<Either<StreamControlMessage, Signal>> sink =
                    builder.add(getStreamControlOrSignalSink());

            final UniformFanInShape<DittoRuntimeException, DittoRuntimeException> exceptionMerger =
                    builder.add(Merge.create(2, true));

            builder.from(strictify.out()).toInlet(select.in());
            builder.from(select.out0()).toInlet(rateLimiter.in());
            builder.from(select.out1()).toFanIn(exceptionMerger);
            builder.from(rateLimiter.out0()).to(sink);
            builder.from(rateLimiter.out1()).via(droppedCounter).toFanIn(exceptionMerger);

            return FlowShape.of(strictify.in(), exceptionMerger.out());
        }));
    }

    private Graph<SinkShape<Either<StreamControlMessage, Signal>>, ?> getStreamControlOrSignalSink() {

        return Sink.foreach(either -> {
            final Object streamControlMessageOrSignal = either.isLeft() ? either.left().get() : either.right().get();
            streamingActor.tell(streamControlMessageOrSignal, ActorRef.noSender());
        });
    }


    private Flow<Message, String, NotUsed> getStrictifyFlow(final HttpRequest request,
            final CharSequence correlationId) {
        return Flow.<Message>create()
                .via(Flow.fromFunction(msg -> {
                    IN_COUNTER.increment();
                    return msg;
                }))
                .filter(Message::isText)
                .map(Message::asTextMessage)
                .map(textMsg -> {
                    if (textMsg.isStrict()) {
                        return Source.single(textMsg.getStrictText());
                    } else {
                        return textMsg.getStreamedText();
                    }
                })
                .flatMapConcat(textMsg -> textMsg.fold("", (str1, str2) -> str1 + str2))
                .via(incomingMessageSniffer.toAsyncFlow(request))
                .via(Flow.fromFunction(result -> {
                    LOGGER.withCorrelationId(correlationId).debug("Received incoming WebSocket message: {}", result);
                    return result;
                }))
                .withAttributes(Attributes.createLogLevels(Logging.DebugLevel(), Logging.DebugLevel(),
                        Logging.WarningLevel()));

    }

    private Graph<FanOutShape2<String, Either<StreamControlMessage, Signal>, DittoRuntimeException>, NotUsed>
    selectStreamControlOrSignal(
            final JsonSchemaVersion version,
            final CharSequence connectionCorrelationId,
            final AuthorizationContext connectionAuthContext,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter) {

        final ProtocolMessageExtractor protocolMessageExtractor =
                new ProtocolMessageExtractor(connectionAuthContext, connectionCorrelationId);

        return Filter.multiplexByEither(cmdString -> {
            final Optional<StreamControlMessage> streamControlMessage = protocolMessageExtractor.apply(cmdString);
            if (streamControlMessage.isPresent()) {
                return Right.apply(Left.apply(streamControlMessage.get()));
            } else {
                try {
                    final Signal<?> signal = buildSignal(cmdString, version, connectionCorrelationId,
                            connectionAuthContext, additionalHeaders, adapter, headerTranslator);
                    return Right.apply(Right.apply(signal));
                } catch (final DittoRuntimeException dre) {
                    // This is a client error usually; log at level DEBUG without stack trace.
                    LOGGER.withCorrelationId(dre)
                            .debug("DittoRuntimeException building signal from <{}>: <{}>", cmdString, dre);
                    return Left.apply(dre);
                } catch (final Exception throwable) {
                    LOGGER.warn("Error building signal from <{}>: {}: <{}>", cmdString,
                            throwable.getClass().getSimpleName(), throwable.getMessage());
                    final DittoRuntimeException dittoRuntimeException = GatewayInternalErrorException.newBuilder()
                            .cause(throwable)
                            .build();
                    return Left.apply(dittoRuntimeException);
                }
            }
        });
    }

    private Flow<DittoRuntimeException, Message, NotUsed> createOutgoing(
            final JsonSchemaVersion version,
            final CharSequence connectionCorrelationId,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter,
            final HttpRequest request,
            final WebsocketConfig websocketConfig,
            @Nullable final SignalEnrichmentFacade signalEnrichmentFacade) {

        final Optional<JsonWebToken> optJsonWebToken = extractJwtFromRequestIfPresent(request);

        final Source<SessionedJsonifiable, ActorRef> publisherSource =
                Source.actorPublisher(EventAndResponsePublisher.props(
                        websocketConfig.getPublisherBackpressureBufferSize()));

        final Source<SessionedJsonifiable, NotUsed> eventAndResponseSource = publisherSource.mapMaterializedValue(
                publisherActor -> {
                    webSocketSupervisor.supervise(publisherActor, connectionCorrelationId, additionalHeaders);
                    streamingActor.tell(
                            new Connect(publisherActor, connectionCorrelationId, STREAMING_TYPE_WS, version,
                                    optJsonWebToken.map(JsonWebToken::getExpirationTime).orElse(null)),
                            ActorRef.noSender());
                    return NotUsed.getInstance();
                })
                .recoverWithRetries(1, new PFBuilder<Throwable, Source<SessionedJsonifiable, NotUsed>>()
                        .match(GatewayWebsocketSessionExpiredException.class,
                                ex -> {
                                    LOGGER.withCorrelationId(connectionCorrelationId)
                                            .info("WebSocket connection terminated because JWT expired!");
                                    return Source.empty();
                                }).match(GatewayWebsocketSessionClosedException.class,
                                ex -> {
                                    LOGGER.withCorrelationId(connectionCorrelationId).info("WebSocket connection" +
                                            " terminated because authorization context changed!");
                                    return Source.empty();
                                })
                        .build());

        final Flow<DittoRuntimeException, SessionedJsonifiable, NotUsed> errorFlow =
                Flow.fromFunction(SessionedJsonifiable::error);

        final int signalEnrichmentParallelism = streamingConfig.getParallelism();
        final Flow<SessionedJsonifiable, Message, NotUsed> messageFlow =
                Flow.<SessionedJsonifiable>create()
                        .mapAsync(signalEnrichmentParallelism, postprocess(adapter, signalEnrichmentFacade))
                        .mapConcat(x -> x)
                        .via(Flow.fromFunction(result -> {
                            LOGGER.withCorrelationId(connectionCorrelationId)
                                    .debug("Sending outgoing WebSocket message: {}", result);
                            return result;
                        }))
                        .via(outgoingMessageSniffer.toAsyncFlow(request))
                        .<Message>map(TextMessage::create)
                        .via(Flow.fromFunction(msg -> {
                            OUT_COUNTER.increment();
                            return msg;
                        }));

        return joinOutgoingFlows(eventAndResponseSource, errorFlow, messageFlow);
    }

    @SuppressWarnings("unchecked")
    private static <T> Flow<DittoRuntimeException, Message, NotUsed> joinOutgoingFlows(
            final Source<T, NotUsed> eventAndResponseSource,
            final Flow<DittoRuntimeException, T, NotUsed> errorFlow,
            final Flow<T, Message, NotUsed> messageFlow) {

        return Flow.fromGraph(GraphDSL.create3(eventAndResponseSource, errorFlow, messageFlow,
                (notUsed1, notUsed2, notUsed3) -> notUsed1,
                (builder, eventsAndResponses, errors, messages) -> {
                    final UniformFanInShape<T, T> merge = builder.add(Merge.create(2, true));

                    builder.from(eventsAndResponses).toFanIn(merge);
                    builder.from(errors).toFanIn(merge);
                    builder.from(merge.out()).toInlet(messages.in());

                    return FlowShape.of(errors.in(), messages.out());
                }));
    }

    private static <T> Graph<FanOutShape2<Either<T, Signal>, Either<T, Signal>, DittoRuntimeException>, NotUsed>
    getRateLimiter(final WebsocketConfig websocketConfig) {
        final Duration rateLimitInterval = websocketConfig.getThrottlingConfig().getInterval();
        final int throttlingLimit = websocketConfig.getThrottlingConfig().getLimit();
        final int messagesPerInterval =
                Math.max(throttlingLimit, (int) (throttlingLimit * websocketConfig.getThrottlingRejectionFactor()));
        return LimitRateByRejection.of(rateLimitInterval, messagesPerInterval, either -> {
            final TooManyRequestsException.Builder builder =
                    TooManyRequestsException.newBuilder().retryAfter(rateLimitInterval);
            if (either.isRight()) {
                builder.dittoHeaders(either.right().get().getDittoHeaders());
            }
            return builder.build();
        });
    }

    private static Signal buildSignal(final String cmdString,
            final JsonSchemaVersion version,
            final CharSequence connectionCorrelationId,
            final AuthorizationContext connectionAuthContext,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter,
            final HeaderTranslator headerTranslator) {

        // initial internal header values
        final DittoHeaders initialInternalHeaders = DittoHeaders.newBuilder()
                .schemaVersion(version)
                .authorizationContext(connectionAuthContext)
                .correlationId(connectionCorrelationId) // for logging
                .origin(connectionCorrelationId)
                .build();

        if (cmdString.isEmpty()) {
            final RuntimeException cause = new IllegalArgumentException("Empty json.");
            throw new DittoJsonException(cause, initialInternalHeaders);
        }

        final JsonifiableAdaptable jsonifiableAdaptable = wrapJsonRuntimeException(cmdString,
                DittoHeaders.empty(), // unused
                (s, unused) -> ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(s)));

        final Signal<? extends Signal> signal;
        try {
            signal = adapter.fromAdaptable(jsonifiableAdaptable);
        } catch (final DittoRuntimeException e) {
            throw e.setDittoHeaders(e.getDittoHeaders().toBuilder().origin(connectionCorrelationId).build());
        }

        final DittoHeadersBuilder<?, ?> internalHeadersBuilder = DittoHeaders.newBuilder();

        try (final AutoCloseableSlf4jLogger logger = LOGGER.setCorrelationId(connectionCorrelationId)) {
            logger.debug("WebSocket message has been converted to signal <{}>.", signal);
            final DittoHeaders signalHeaders = signal.getDittoHeaders();

            // add initial internal header values
            logger.trace("Adding initialInternalHeaders: <{}>.", initialInternalHeaders);
            internalHeadersBuilder.putHeaders(initialInternalHeaders);
            // add headers given by parent route first so that protocol message may override them
            final Map<String, String> wellKnownAdditionalHeaders =
                    headerTranslator.retainKnownHeaders(additionalHeaders);
            logger.trace("Adding wellKnownAdditionalHeaders: <{}>.", wellKnownAdditionalHeaders);
            internalHeadersBuilder.putHeaders(wellKnownAdditionalHeaders);
            // add any headers from protocol adapter to internal headers
            logger.trace("Adding signalHeaders: <{}>.", signalHeaders);
            internalHeadersBuilder.putHeaders(signalHeaders);
            // generate correlation ID if it is not set in protocol message
            if (signalHeaders.getCorrelationId().isEmpty()) {
                final String correlationId = UUID.randomUUID().toString();
                logger.trace("Adding generated correlationId: <{}>.", correlationId);
                internalHeadersBuilder.correlationId(correlationId);
            }
            logger.debug("Generated internalHeaders are: <{}>.", internalHeadersBuilder);
        }

        return signal.setDittoHeaders(internalHeadersBuilder.build());
    }

    private static Function<SessionedJsonifiable, CompletionStage<Collection<String>>> postprocess(
            final ProtocolAdapter adapter,
            @Nullable final SignalEnrichmentFacade facade) {

        return sessionedJsonifiable -> {
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable = sessionedJsonifiable.getJsonifiable();
            if (jsonifiable instanceof StreamingAck) {
                return CompletableFuture.completedFuture(
                        Collections.singletonList(streamingAckToString((StreamingAck) jsonifiable))
                );
            }

            final Adaptable adaptable = jsonifiableToAdaptable(jsonifiable, adapter);
            final CompletionStage<JsonObject> extraFuture = sessionedJsonifiable.retrieveExtraFields(facade);
            return extraFuture.<Collection<String>>thenApply(extra ->
                    matchesFilter(sessionedJsonifiable, extra)
                            ? Collections.singletonList(toJsonStringWithExtra(adaptable, extra))
                            : Collections.emptyList())
                    .exceptionally(error -> WebSocketRoute.reportEnrichmentError(error, adapter, adaptable));
        };
    }

    private static Collection<String> reportEnrichmentError(final Throwable error,
            final ProtocolAdapter adapter,
            final Adaptable adaptable) {
        final DittoRuntimeException errorToReport;
        if (error instanceof DittoRuntimeException) {
            errorToReport = ((DittoRuntimeException) error);
        } else {
            errorToReport = SignalEnrichmentFailedException.newBuilder()
                    .dittoHeaders(adaptable.getDittoHeaders())
                    .cause(error)
                    .build();
        }
        LOGGER.withCorrelationId(adaptable.getDittoHeaders())
                .error("Signal enrichment failed due to: {}", error.getMessage(), errorToReport);

        final JsonifiableAdaptable errorAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(adapter.toAdaptable((Signal<?>)
                        ThingErrorResponse.of(
                                ThingId.of(adaptable.getTopicPath().getNamespace(), adaptable.getTopicPath().getId()),
                                errorToReport,
                                adaptable.getDittoHeaders()
                        )
                ));
        return Collections.singletonList(errorAdaptable.toJsonString());
    }

    private static String toJsonStringWithExtra(final Adaptable adaptable, final JsonObject extra) {
        final Adaptable enrichedAdaptable =
                extra.isEmpty() ? adaptable : ProtocolFactory.setExtra(adaptable, extra);
        return ProtocolFactory.wrapAsJsonifiableAdaptable(enrichedAdaptable).toJsonString();
    }

    /**
     * Tests whether a signal together with enriched extra fields pass its filter defined in the session.
     * Always return true for Jsonifiables without any session, e. g., errors, responses, stream control messages.
     *
     * @param sessionedJsonifiable the Jsonifiable with session information attached.
     * @param extra extra fields from signal enrichment.
     * @return whether the Jsonifiable passes filter defined in the session together with the extra fields.
     */
    private static boolean matchesFilter(final SessionedJsonifiable sessionedJsonifiable, final JsonObject extra) {
        final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable = sessionedJsonifiable.getJsonifiable();
        return sessionedJsonifiable.getSession()
                .filter(session -> jsonifiable instanceof Signal)
                .map(session ->
                        // evaluate to false if filter is present but does not match or has insufficient info to match
                        session.matchesFilter(session.mergeThingWithExtra((Signal<?>) jsonifiable, extra))
                )
                .orElse(true);
    }

    private static String streamingAckToString(final StreamingAck streamingAck) {
        final StreamingType streamingType = streamingAck.getStreamingType();
        final boolean subscribed = streamingAck.isSubscribed();
        final String protocolMessage;
        switch (streamingType) {
            case EVENTS:
                protocolMessage = subscribed ? START_SEND_EVENTS.toString() : STOP_SEND_EVENTS.toString();
                break;
            case MESSAGES:
                protocolMessage = subscribed ? START_SEND_MESSAGES.toString() : STOP_SEND_MESSAGES.toString();
                break;
            case LIVE_COMMANDS:
                protocolMessage = subscribed ? START_SEND_LIVE_COMMANDS.toString() : STOP_SEND_LIVE_COMMANDS.toString();
                break;
            case LIVE_EVENTS:
                protocolMessage = subscribed ? START_SEND_LIVE_EVENTS.toString() : STOP_SEND_LIVE_EVENTS.toString();
                break;
            default:
                throw new IllegalArgumentException("Unknown streamingType: " + streamingType);
        }
        return protocolMessage + PROTOCOL_CMD_ACK_SUFFIX;
    }

    private static Adaptable jsonifiableToAdaptable(final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final ProtocolAdapter adapter) {
        final Adaptable adaptable;
        if (jsonifiable instanceof Signal) {
            adaptable = adapter.toAdaptable((Signal<?>) jsonifiable);
        } else if (jsonifiable instanceof DittoRuntimeException) {
            final Signal<?> signal;
            if (jsonifiable instanceof PolicyException) {
                signal = buildPolicyErrorResponse((DittoRuntimeException) jsonifiable);
            } else if (jsonifiable instanceof ThingSearchException) {
                signal = buildSearchErrorResponse((DittoRuntimeException) jsonifiable);
            } else {
                signal = buildThingErrorResponse((DittoRuntimeException) jsonifiable);
            }
            adaptable = adapter.toAdaptable(signal);
        } else {
            throw new IllegalArgumentException("Jsonifiable was neither Signal nor DittoRuntimeException: " +
                    jsonifiable.getClass().getSimpleName());
        }
        return adaptable;
    }

    private static ThingErrorResponse buildThingErrorResponse(final DittoRuntimeException dittoRuntimeException) {
        final DittoHeaders dittoHeaders = dittoRuntimeException.getDittoHeaders();
        final String nullableEntityId = dittoHeaders.get(DittoHeaderDefinition.ENTITY_ID.getKey());
        return nullableEntityId != null
                ? ThingErrorResponse.of(ThingId.of(nullableEntityId), dittoRuntimeException, dittoHeaders)
                : ThingErrorResponse.of(dittoRuntimeException, dittoHeaders);
    }

    private static PolicyErrorResponse buildPolicyErrorResponse(final DittoRuntimeException dittoRuntimeException) {
        final DittoHeaders dittoHeaders = dittoRuntimeException.getDittoHeaders();
        final String nullableEntityId = dittoHeaders.get(DittoHeaderDefinition.ENTITY_ID.getKey());
        return nullableEntityId != null
                ? PolicyErrorResponse.of(PolicyId.of(nullableEntityId), dittoRuntimeException, dittoHeaders)
                : PolicyErrorResponse.of(dittoRuntimeException, dittoHeaders);
    }

    private static SearchErrorResponse buildSearchErrorResponse(final DittoRuntimeException dittoRuntimeException) {
        final DittoHeaders dittoHeaders = dittoRuntimeException.getDittoHeaders();
        return SearchErrorResponse.of(dittoRuntimeException, dittoHeaders);
    }

    private static Optional<JsonWebToken> extractJwtFromRequestIfPresent(final HttpRequest request) {
        return request.getHeader(HttpHeader.AUTHORIZATION.toString())
                .map(akka.http.javadsl.model.HttpHeader::value)
                .filter(s -> s.startsWith(BEARER))
                .map(ImmutableJsonWebToken::fromAuthorization);
    }

    /**
     * Null implementation for {@link WebSocketAuthorizationEnforcer} which does nothing.
     */
    private static final class NoOpAuthorizationEnforcer implements WebSocketAuthorizationEnforcer {

        @Override
        public void checkAuthorization(final DittoHeaders dittoHeaders) {

            // Does nothing.
        }

    }

    /**
     * Null implementation for {@link WebSocketSupervisor} which does nothing.
     */
    private static final class NoOpWebSocketSupervisor implements WebSocketSupervisor {

        @Override
        public void supervise(final ActorRef webSocketActor, final CharSequence connectionCorrelationId,
                final DittoHeaders dittoHeaders) {

            // Does nothing.
        }

    }

}
