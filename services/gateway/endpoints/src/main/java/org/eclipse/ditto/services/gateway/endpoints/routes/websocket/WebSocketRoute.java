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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.jwt.ImmutableJsonWebToken;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.endpoints.utils.EventSniffer;
import org.eclipse.ditto.services.gateway.endpoints.utils.GatewaySignalEnrichmentProvider;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.ResponsePublished;
import org.eclipse.ditto.services.gateway.streaming.StreamControlMessage;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.gateway.streaming.WebsocketConfig;
import org.eclipse.ditto.services.gateway.streaming.actors.CommandSubscriber;
import org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher;
import org.eclipse.ditto.services.gateway.streaming.actors.SessionedJsonifiable;
import org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.models.things.SignalEnrichmentFacade;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.LimitRateByRejection;
import org.eclipse.ditto.services.utils.akka.logging.AutoCloseableSlf4jLogger;
import org.eclipse.ditto.services.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayWebsocketSessionClosedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayWebsocketSessionExpiredException;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.events.base.Event;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.EventStream;
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
    private final EventStream eventStream;

    private EventSniffer<String> incomingMessageSniffer;
    private EventSniffer<String> outgoingMessageSniffer;
    private WebSocketAuthorizationEnforcer authorizationEnforcer;
    private WebSocketSupervisor webSocketSupervisor;
    @Nullable private GatewaySignalEnrichmentProvider signalEnrichmentProvider;

    private WebSocketRoute(final ActorRef streamingActor, final EventStream eventStream) {

        this.streamingActor = checkNotNull(streamingActor, "streamingActor");
        this.eventStream = checkNotNull(eventStream, "eventStream");

        final EventSniffer<String> noOpEventSniffer = EventSniffer.noOp();
        incomingMessageSniffer = noOpEventSniffer;
        outgoingMessageSniffer = noOpEventSniffer;
        authorizationEnforcer = new NoOpAuthorizationEnforcer();
        webSocketSupervisor = new NoOpWebSocketSupervisor();
        signalEnrichmentProvider = null;
    }

    /**
     * Returns an instance of this class.
     *
     * @param streamingActor the {@link org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor} reference.
     * @param eventStream eventStream used to publish events within the actor system
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static WebSocketRoute getInstance(final ActorRef streamingActor, final EventStream eventStream) {

        return new WebSocketRoute(streamingActor, eventStream);
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

    /**
     * Builds the {@code /ws} route.
     *
     * @return the {@code /ws} route.
     */
    @Override
    public Route build(final Integer version,
            final CharSequence correlationId,
            final AuthorizationContext connectionAuthContext,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter chosenProtocolAdapter) {

        return Directives.extractUpgradeToWebSocket(
                upgradeToWebSocketHeader -> Directives.extractRequest(
                        request -> {
                            authorizationEnforcer.checkAuthorization(request, connectionAuthContext, additionalHeaders);
                            return Directives.completeWithFuture(
                                    createWebSocket(upgradeToWebSocketHeader, version, correlationId.toString(),
                                            connectionAuthContext, additionalHeaders, chosenProtocolAdapter, request));
                        }));
    }

    private CompletionStage<WebsocketConfig> retrieveWebsocketConfig() {
        return Patterns.ask(streamingActor, StreamingActor.Control.RETRIEVE_WEBSOCKET_CONFIG, CONFIG_ASK_TIMEOUT)
                .thenApply(reply -> (WebsocketConfig) reply); // fail future with ClassCastException on type error
    }

    private CompletionStage<HttpResponse> createWebSocket(final UpgradeToWebSocket upgradeToWebSocket,
            final Integer version,
            final String connectionCorrelationId,
            final AuthorizationContext authContext,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter,
            final HttpRequest request) {

        final SignalEnrichmentFacade signalEnrichmentFacade =
                signalEnrichmentProvider == null ? null : signalEnrichmentProvider.createFacade(request);

        LOGGER.withCorrelationId(connectionCorrelationId)
                .info("Creating WebSocket for connection authContext: <{}>", authContext);

        return retrieveWebsocketConfig().thenApply(websocketConfig -> {
            final Flow<Message, DittoRuntimeException, NotUsed> incoming =
                    createIncoming(version, connectionCorrelationId, authContext, additionalHeaders, adapter, request,
                            websocketConfig);
            final Flow<DittoRuntimeException, Message, NotUsed> outgoing =
                    createOutgoing(connectionCorrelationId, additionalHeaders, adapter, request, websocketConfig,
                            signalEnrichmentFacade);

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
     *                  v                               |
     *      Filter.multiplexByEither                    |
     *       +                  +                       |
     *       |                  |                       |
     *       v                  |                       |
     * Control Msg:             |                       |
     * StreamSessionActor       |                       |
     *                          |                       |
     *                          v                       |
     *                       Signal:                    |
     *                       CommandSubscriber          |
     *                                                  |
     *                                                  |
     *                  +-------------------------------+
     *                  |
     *                  v
     *         DittoRuntimeException
     */
    @SuppressWarnings("unchecked")
    private Flow<Message, DittoRuntimeException, NotUsed> createIncoming(final Integer version,
            final String connectionCorrelationId,
            final AuthorizationContext connectionAuthContext,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter,
            final HttpRequest request,
            final WebsocketConfig websocketConfig) {

        return Flow.fromGraph(GraphDSL.create(builder -> {

            final FlowShape<Message, String> strictify =
                    builder.add(getStrictifyFlow(request, connectionCorrelationId).via(throttle(websocketConfig)));

            final FanOutShape2<String, Either<StreamControlMessage, Signal>, DittoRuntimeException> select =
                    builder.add(selectStreamControlOrSignal(version, connectionCorrelationId, connectionAuthContext,
                            additionalHeaders, adapter));

            final FanOutShape2<Either<StreamControlMessage, Signal>, Either<StreamControlMessage, Signal>,
                    DittoRuntimeException> rateLimiter = builder.add(getRateLimiter(websocketConfig));

            final FlowShape<DittoRuntimeException, DittoRuntimeException> droppedCounter =
                    builder.add(Flow.fromFunction(e -> {
                        DROPPED_COUNTER.increment();
                        return e;
                    }));

            final SinkShape<Either<StreamControlMessage, Signal>> sink =
                    builder.add(getStreamControlOrSignalSink(websocketConfig));

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

    @SuppressWarnings("unchecked")
    private Graph<SinkShape<Either<StreamControlMessage, Signal>>, NotUsed> getStreamControlOrSignalSink(
            final WebsocketConfig config) {

        return GraphDSL.create(builder -> {
            final FanOutShape2<Either<StreamControlMessage, Signal>, Signal, StreamControlMessage> multiplexer =
                    builder.add(Filter.multiplexByEither(java.util.function.Function.identity()));
            final SinkShape<Signal> signalSink = builder.add(getCommandSubscriberSink(config));
            final SinkShape<StreamControlMessage> streamControlSink = builder.add(getStreamingActorSink());

            builder.from(multiplexer.out0()).to(signalSink);
            builder.from(multiplexer.out1()).to(streamControlSink);

            return SinkShape.of(multiplexer.in());
        });
    }

    private Sink<Signal, ActorRef> getCommandSubscriberSink(final WebsocketConfig websocketConfig) {
        final Props commandSubscriberProps =
                CommandSubscriber.props(streamingActor, websocketConfig.getSubscriberBackpressureQueueSize(),
                        eventStream);
        return Sink.actorSubscriber(commandSubscriberProps);
    }

    private Flow<Message, String, NotUsed> getStrictifyFlow(final HttpRequest request, final String correlationId) {
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

    private Sink<StreamControlMessage, ?> getStreamingActorSink() {
        return Sink.foreach(streamControlMessage -> streamingActor.tell(streamControlMessage, ActorRef.noSender()));
    }

    private Graph<FanOutShape2<String, Either<StreamControlMessage, Signal>, DittoRuntimeException>, NotUsed>
    selectStreamControlOrSignal(
            final Integer version,
            final String connectionCorrelationId,
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
                    final Signal signal =
                            buildSignal(cmdString, version, connectionCorrelationId, connectionAuthContext,
                                    additionalHeaders, adapter);
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

    private Flow<DittoRuntimeException, Message, NotUsed> createOutgoing(final String connectionCorrelationId,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter,
            final HttpRequest request,
            final WebsocketConfig websocketConfig,
            final SignalEnrichmentFacade signalEnrichmentFacade) {

        final Optional<JsonWebToken> optJsonWebToken = extractJwtFromRequestIfPresent(request);

        final Source<SessionedJsonifiable, ActorRef> publisherSource =
                Source.actorPublisher(EventAndResponsePublisher.props(
                        websocketConfig.getPublisherBackpressureBufferSize()));

        final Source<SessionedJsonifiable, NotUsed> eventAndResponseSource = publisherSource.mapMaterializedValue(
                publisherActor -> {
                    webSocketSupervisor.supervise(publisherActor, connectionCorrelationId, additionalHeaders);
                    streamingActor.tell(
                            new Connect(publisherActor, connectionCorrelationId, STREAMING_TYPE_WS,
                                    optJsonWebToken.map(JsonWebToken::getExpirationTime).orElse(null)),
                            ActorRef.noSender());
                    return NotUsed.getInstance();
                })
                .map(this::publishResponsePublishedEvent)
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

        // TODO: add own key to websocket publisher config
        final int signalEnrichmentParallelism = websocketConfig.getPublisherBackpressureBufferSize();
        final Flow<SessionedJsonifiable, Message, NotUsed> messageFlow =
                Flow.<SessionedJsonifiable>create()
                        .mapAsync(signalEnrichmentParallelism, jsonifiableToString(adapter, signalEnrichmentFacade))
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

    private SessionedJsonifiable publishResponsePublishedEvent(final SessionedJsonifiable jsonifiable) {
        final Jsonifiable.WithPredicate<JsonObject, JsonField> content = jsonifiable.getJsonifiable();
        if (content instanceof CommandResponse || content instanceof DittoRuntimeException) {
            // only create ResponsePublished for CommandResponses and DittoRuntimeExceptions
            // not for Events with the same correlation ID
            jsonifiable.getDittoHeaders()
                    .getCorrelationId()
                    .map(ResponsePublished::new)
                    .ifPresent(eventStream::publish);
        }
        return jsonifiable;
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

    private static <T> Flow<T, T, NotUsed> throttle(final WebsocketConfig websocketConfig) {
        return Flow.<T>create()
                .throttle(websocketConfig.getThrottlingConfig().getLimit(),
                        websocketConfig.getThrottlingConfig().getInterval());
    }

    private static Signal buildSignal(final String cmdString,
            final Integer version,
            final String connectionCorrelationId,
            final AuthorizationContext connectionAuthContext,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter) {

        final JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.forInt(version)
                .orElseThrow(() -> CommandNotSupportedException.newBuilder(version).build());

        // initial internal header values
        final DittoHeaders initialInternalHeaders = DittoHeaders.newBuilder()
                .schemaVersion(jsonSchemaVersion)
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

        final DittoHeadersBuilder internalHeadersBuilder = DittoHeaders.newBuilder();

        try (final AutoCloseableSlf4jLogger logger = LOGGER.setCorrelationId(connectionCorrelationId)) {
            logger.debug("WebSocket message has been converted to signal <{}>.", signal);
            final DittoHeaders signalHeaders = signal.getDittoHeaders();

            // add initial internal header values
            logger.trace("Adding initialInternalHeaders: <{}>.", initialInternalHeaders);
            internalHeadersBuilder.putHeaders(initialInternalHeaders);
            // add headers given by parent route first so that protocol message may override them
            logger.trace("Adding additionalHeaders: <{}>.", additionalHeaders);
            internalHeadersBuilder.putHeaders(additionalHeaders);
            // add any headers from protocol adapter to internal headers
            logger.trace("Adding signalHeaders: <{}>.", signalHeaders);
            internalHeadersBuilder.putHeaders(signalHeaders);
            // generate correlation ID if it is not set in protocol message
            if (!signalHeaders.getCorrelationId().isPresent()) {
                final String correlationId = UUID.randomUUID().toString();
                logger.trace("Adding generated correlationId: <{}>.", correlationId);
                internalHeadersBuilder.correlationId(correlationId);
            }
            logger.debug("Generated internalHeaders are: <{}>.", internalHeadersBuilder);
        }

        return signal.setDittoHeaders(internalHeadersBuilder.build());
    }

    private static Function<SessionedJsonifiable, CompletionStage<String>> jsonifiableToString(
            final ProtocolAdapter adapter,
            final SignalEnrichmentFacade signalEnrichmentFacade) {
        return sessionedJsonifiable -> {
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable = sessionedJsonifiable.getJsonifiable();
            if (jsonifiable instanceof StreamingAck) {
                return CompletableFuture.completedFuture(streamingAckToString((StreamingAck) jsonifiable));
            }

            final Adaptable adaptable;
            if (sessionedJsonifiable.getDittoHeaders().getChannel().isPresent()) {
                // if channel was present in headers, use that one:
                final TopicPath.Channel channel =
                        TopicPath.Channel.forName(sessionedJsonifiable.getDittoHeaders().getChannel().get())
                                .orElse(TopicPath.Channel.TWIN);
                adaptable = jsonifiableToAdaptable(jsonifiable, channel, adapter);
            } else if (jsonifiable instanceof Signal && isLiveSignal((Signal<?>) jsonifiable)) {
                adaptable = jsonifiableToAdaptable(jsonifiable, TopicPath.Channel.LIVE, adapter);
            } else {
                adaptable = jsonifiableToAdaptable(jsonifiable, TopicPath.Channel.TWIN, adapter);
            }
            final CompletionStage<Adaptable> enrichedAdaptableFuture =
                    sessionedJsonifiable.retrieveExtraFields(signalEnrichmentFacade)
                            .exceptionally(error -> {
                                if (error instanceof DittoRuntimeException) {
                                    return ((DittoRuntimeException) error).toJson();
                                } else {
                                    return SignalEnrichmentFailedException.newBuilder().build().toJson();
                                }
                            })
                            .thenApply(extra -> extra.isEmpty()
                                    ? adaptable
                                    : ProtocolFactory.setExtra(adaptable, extra));

            return enrichedAdaptableFuture.thenApply(ProtocolFactory::wrapAsJsonifiableAdaptable)
                    .thenApply(Jsonifiable::toJsonString);
        };
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

    private static boolean isLiveSignal(final Signal<?> signal) {
        return StreamingType.isLiveSignal(signal);
    }

    private static Adaptable jsonifiableToAdaptable(final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final TopicPath.Channel channel, final ProtocolAdapter adapter) {
        final Adaptable adaptable;
        if (jsonifiable instanceof Command) {
            adaptable = adapter.toAdaptable((Command) jsonifiable, channel);
        } else if (jsonifiable instanceof Event) {
            adaptable = adapter.toAdaptable((Event) jsonifiable, channel);
        } else if (jsonifiable instanceof CommandResponse) {
            adaptable = adapter.toAdaptable((CommandResponse) jsonifiable, channel);
        } else if (jsonifiable instanceof DittoRuntimeException) {
            final DittoRuntimeException dittoRuntimeException = (DittoRuntimeException) jsonifiable;
            final DittoHeaders enhancedHeaders = dittoRuntimeException.getDittoHeaders().toBuilder()
                    .channel(channel.getName())
                    .build();
            final String nullableThingId = enhancedHeaders.get(MessageHeaderDefinition.THING_ID.getKey());
            final ThingErrorResponse errorResponse = nullableThingId != null
                    ? ThingErrorResponse.of(ThingId.of(nullableThingId), dittoRuntimeException, enhancedHeaders)
                    : ThingErrorResponse.of(dittoRuntimeException, enhancedHeaders);
            adaptable = adapter.toAdaptable(errorResponse, channel);
        } else {
            throw new IllegalArgumentException("Jsonifiable was neither Command nor CommandResponse nor"
                    + " Event nor DittoRuntimeException: " + jsonifiable.getClass().getSimpleName());
        }
        return adaptable;
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
        public void checkAuthorization(final HttpRequest request, final AuthorizationContext authorizationContext,
                final DittoHeaders dittoHeaders) {

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
