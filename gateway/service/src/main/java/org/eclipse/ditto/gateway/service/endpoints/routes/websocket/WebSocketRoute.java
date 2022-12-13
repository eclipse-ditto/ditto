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
package org.eclipse.ditto.gateway.service.endpoints.routes.websocket;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.SignalEnrichmentFailedException;
import org.eclipse.ditto.base.model.exceptions.TooManyRequestsException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.gateway.api.GatewayInternalErrorException;
import org.eclipse.ditto.gateway.api.GatewayWebsocketSessionAbortedException;
import org.eclipse.ditto.gateway.api.GatewayWebsocketSessionClosedException;
import org.eclipse.ditto.gateway.api.GatewayWebsocketSessionExpiredException;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.utils.GatewaySignalEnrichmentProvider;
import org.eclipse.ditto.gateway.service.security.HttpHeader;
import org.eclipse.ditto.gateway.service.streaming.StreamingAuthorizationEnforcer;
import org.eclipse.ditto.gateway.service.streaming.actors.SessionedJsonifiable;
import org.eclipse.ditto.gateway.service.streaming.actors.StreamingActor;
import org.eclipse.ditto.gateway.service.streaming.actors.SupervisedStream;
import org.eclipse.ditto.gateway.service.streaming.signals.Connect;
import org.eclipse.ditto.gateway.service.streaming.signals.IncomingSignal;
import org.eclipse.ditto.gateway.service.streaming.signals.StreamControlMessage;
import org.eclipse.ditto.gateway.service.streaming.signals.StreamingAck;
import org.eclipse.ditto.gateway.service.util.config.streaming.StreamingConfig;
import org.eclipse.ditto.gateway.service.util.config.streaming.WebsocketConfig;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.akka.controlflow.Filter;
import org.eclipse.ditto.internal.utils.akka.controlflow.LimitRateByRejection;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.SpanTagKey;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.policies.model.PolicyException;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.protocol.mappingstrategies.IllegalAdaptableException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.thingsearch.model.ThingSearchException;
import org.eclipse.ditto.thingsearch.model.signals.commands.SearchErrorResponse;
import org.slf4j.Logger;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.model.ws.WebSocketUpgrade;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.japi.Pair;
import akka.japi.function.Function;
import akka.japi.pf.PFBuilder;
import akka.pattern.Patterns;
import akka.stream.Attributes;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.SharedKillSwitch;
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

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory.getThreadSafeLogger(WebSocketRoute.class);

    /**
     * Ask timeout for the local streaming actor.
     */
    private static final Duration LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5L);

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
    private static final String MDC_CONNECTION_CORRELATION_ID = "connection-correlation-id";

    private final SharedKillSwitch wsKillSwitch = KillSwitches.shared(WebSocketRoute.class.getSimpleName());

    private final ActorRef streamingActor;
    private final StreamingConfig streamingConfig;
    private final Materializer materializer;

    private IncomingWebSocketEventSniffer incomingMessageSniffer;
    private OutgoingWebSocketEventSniffer outgoingMessageSniffer;
    private StreamingAuthorizationEnforcer authorizationEnforcer;
    private WebSocketSupervisor webSocketSupervisor;
    @Nullable private GatewaySignalEnrichmentProvider signalEnrichmentProvider;
    private HeaderTranslator headerTranslator;
    private WebSocketConfigProvider webSocketConfigProvider;


    private WebSocketRoute(final ActorSystem actorSystem,
            final ActorRef streamingActor,
            final StreamingConfig streamingConfig,
            final Materializer materializer) {

        this.streamingActor = checkNotNull(streamingActor, "streamingActor");
        this.streamingConfig = streamingConfig;

        final var config = actorSystem.settings().config();
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(config);
        incomingMessageSniffer = IncomingWebSocketEventSniffer.get(actorSystem, dittoExtensionsConfig);
        outgoingMessageSniffer = OutgoingWebSocketEventSniffer.get(actorSystem, dittoExtensionsConfig);
        final var websocketConfig = ScopedConfig.getOrEmpty(config, "ditto.gateway.streaming.websocket");
        authorizationEnforcer = StreamingAuthorizationEnforcer.get(actorSystem, websocketConfig);
        webSocketSupervisor = WebSocketSupervisor.get(actorSystem, dittoExtensionsConfig);
        webSocketConfigProvider = WebSocketConfigProvider.get(actorSystem, dittoExtensionsConfig);
        signalEnrichmentProvider = null;
        headerTranslator = HeaderTranslator.empty();
        this.materializer = materializer;
    }

    /**
     * Returns an instance of this class.
     *
     * @param actorSystem the actorSystem in which the route should be instantiated.
     * @param streamingActor the {@link org.eclipse.ditto.gateway.service.streaming.actors.StreamingActor} reference.
     * @param streamingConfig the streaming configuration.
     * @param materializer the materializer to use for stream materialization.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static WebSocketRoute getInstance(final ActorSystem actorSystem,
            final ActorRef streamingActor,
            final StreamingConfig streamingConfig,
            final Materializer materializer) {

        return new WebSocketRoute(actorSystem, streamingActor, streamingConfig, materializer);
    }

    @Override
    public WebSocketRouteBuilder withIncomingEventSniffer(final IncomingWebSocketEventSniffer eventSniffer) {
        incomingMessageSniffer = checkNotNull(eventSniffer, "eventSniffer");
        return this;
    }

    @Override
    public WebSocketRouteBuilder withOutgoingEventSniffer(final OutgoingWebSocketEventSniffer eventSniffer) {
        outgoingMessageSniffer = checkNotNull(eventSniffer, "eventSniffer");
        return this;
    }

    @Override
    public WebSocketRouteBuilder withAuthorizationEnforcer(final StreamingAuthorizationEnforcer enforcer) {
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

    @Override
    public WebSocketRouteBuilder withWebSocketConfigProvider(final WebSocketConfigProvider webSocketConfigProvider) {
        this.webSocketConfigProvider = checkNotNull(webSocketConfigProvider, "webSocketConfigProvider");
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
            final ProtocolAdapter chosenProtocolAdapter,
            final RequestContext ctx) {

        return Directives.extractWebSocketUpgrade(websocketUpgrade -> Directives.extractRequest(request -> {
            final CompletionStage<DittoHeaders> checkAuthorization =
                    authorizationEnforcer.checkAuthorization(ctx, dittoHeaders);
            return Directives.completeWithFuture(checkAuthorization.thenCompose(authorizedHeaders ->
                    createWebSocket(websocketUpgrade, version, correlationId.toString(),
                            authorizedHeaders, chosenProtocolAdapter, request)));
        }));
    }

    private CompletionStage<WebsocketConfig> retrieveWebsocketConfig() {
        return Patterns.ask(streamingActor, StreamingActor.Control.RETRIEVE_WEBSOCKET_CONFIG, LOCAL_ASK_TIMEOUT)
                .thenApply(WebsocketConfig.class::cast); // fail future with ClassCastException on type error
    }

    private CompletionStage<HttpResponse> createWebSocket(final WebSocketUpgrade upgradeToWebSocket,
            final JsonSchemaVersion version,
            final CharSequence connectionCorrelationId,
            final DittoHeaders dittoHeaders,
            final ProtocolAdapter adapter,
            final HttpRequest request) {

        final CompletionStage<SignalEnrichmentFacade> signalEnrichmentFacadeStage = signalEnrichmentProvider == null
                ? CompletableFuture.completedStage(null)
                : signalEnrichmentProvider.getFacade(request);

        final AuthorizationContext authContext = dittoHeaders.getAuthorizationContext();
        final ThreadSafeDittoLogger logger = LOGGER.withMdcEntry(MDC_CONNECTION_CORRELATION_ID,
                connectionCorrelationId);
        logger.info("Creating WebSocket for connection authContext: <{}>", authContext);

        return signalEnrichmentFacadeStage.thenCompose(signalEnrichmentFacade -> retrieveWebsocketConfig()
                .thenApply(overwriteWebSocketConfig(dittoHeaders))
                .thenApply(websocketConfig -> {
                    final Pair<Connect, Flow<DittoRuntimeException, Message, NotUsed>> outgoing =
                            createOutgoing(version, connectionCorrelationId, authContext, dittoHeaders, adapter,
                                    request, websocketConfig, signalEnrichmentFacade, logger);

                    final Flow<Message, DittoRuntimeException, NotUsed> incoming =
                            createIncoming(version, connectionCorrelationId, authContext, dittoHeaders, adapter,
                                    request, websocketConfig, outgoing.first(), logger);

                    return upgradeToWebSocket.handleMessagesWith(
                            incoming.via(wsKillSwitch.flow()).via(outgoing.second()));
                }));
    }

    private java.util.function.Function<WebsocketConfig, WebsocketConfig> overwriteWebSocketConfig(
            final DittoHeaders dittoHeaders) {
        return wsConfig -> webSocketConfigProvider.apply(dittoHeaders, wsConfig);
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
            final WebsocketConfig websocketConfig,
            final Connect connect,
            final ThreadSafeDittoLogger logger) {

        return Flow.fromGraph(GraphDSL.create(builder -> {

            final FlowShape<Message, String> strictify =
                    builder.add(getStrictifyFlow(request, logger)
                            .via(AbstractRoute.throttleByConfig(websocketConfig.getThrottlingConfig())));

            final FanOutShape2<String, Either<StreamControlMessage, Signal<?>>, DittoRuntimeException> select =
                    builder.add(selectStreamControlOrSignal(version, connectionCorrelationId, connectionAuthContext,
                            dittoHeaders, adapter, logger));

            final FanOutShape2<Either<StreamControlMessage, Signal<?>>, Either<StreamControlMessage, Signal<?>>,
                    DittoRuntimeException> rateLimiter = builder.add(getRateLimiter(websocketConfig));

            final FlowShape<DittoRuntimeException, DittoRuntimeException> droppedCounter =
                    builder.add(Flow.fromFunction(e -> {
                        DROPPED_COUNTER.increment();
                        return e;
                    }));

            final SinkShape<Either<StreamControlMessage, Signal<?>>> sink =
                    builder.add(getStreamControlOrSignalSink(connect));

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

    private Graph<SinkShape<Either<StreamControlMessage, Signal<?>>>, ?> getStreamControlOrSignalSink(
            final Connect connect) {

        final Flow<Either<StreamControlMessage, Signal<?>>, Object, NotUsed> setAckRequestThenMergeLeftAndRight =
                Flow.fromFunction(either -> either.right()
                        .map(IncomingSignal::of)
                        .getOrElse(either.left()::get)
                );

        final Source<ActorRef, ?> sessionActorSource = Source.completionStageSource(
                Patterns.ask(streamingActor, connect, LOCAL_ASK_TIMEOUT)
                        .thenApply(result -> Source.repeat((ActorRef) result))
        );
        final var noOpStreamControlMessage = NoOp.getInstance();

        return setAckRequestThenMergeLeftAndRight.zipWith(sessionActorSource, Pair::create)
                .to(Sink.foreach(pair -> {
                    final var actorRef = pair.second();
                    final var message = pair.first();
                    if (!noOpStreamControlMessage.equals(message)) {
                        actorRef.tell(message, ActorRef.noSender());
                    }
                }));
    }

    private Flow<Message, String, NotUsed> getStrictifyFlow(final HttpRequest request, final Logger logger) {
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
                    logger.debug("Received incoming WebSocket message: {}", result);
                    return result;
                }))
                .withAttributes(Attributes.createLogLevels(Logging.DebugLevel(), Logging.DebugLevel(),
                        Logging.WarningLevel()));
    }

    private Graph<FanOutShape2<String, Either<StreamControlMessage, Signal<?>>, DittoRuntimeException>, NotUsed>
    selectStreamControlOrSignal(
            final JsonSchemaVersion version,
            final CharSequence connectionCorrelationId,
            final AuthorizationContext connectionAuthContext,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter,
            final ThreadSafeDittoLogger logger) {

        final var protocolMsgExtractor = new ProtocolMessageExtractor(connectionAuthContext, connectionCorrelationId);

        return Filter.multiplexByEither(
                cmdString -> {
                    final Optional<StreamControlMessage> streamControlMessage;
                    try {
                        streamControlMessage = protocolMsgExtractor.apply(cmdString);
                    } catch (final DittoRuntimeException dre) {
                        return Left.apply(dre);
                    }
                    Either<DittoRuntimeException, Either<StreamControlMessage, Signal<?>>> result;
                    if (streamControlMessage.isPresent()) {
                        result = Right.apply(Left.apply(streamControlMessage.get()));
                    } else {
                        final var initialInternalHeaders =
                                getInitialInternalHeaders(version, connectionAuthContext, connectionCorrelationId);
                        try {
                            final var signal = buildSignal(connectionCorrelationId,
                                    initialInternalHeaders,
                                    getJsonifiableAdaptableOrThrow(cmdString, initialInternalHeaders),
                                    additionalHeaders,
                                    adapter,
                                    headerTranslator,
                                    logger);
                            final var startedSpan = DittoTracing.newPreparedSpan(
                                            signal.getDittoHeaders(),
                                            SpanOperationName.of("gw_streaming_in_signal")
                                    )
                                    .tag(SpanTagKey.SIGNAL_TYPE.getTagForValue(signal.getType()))
                                    .start();
                            result = Right.apply(
                                    Right.apply(
                                            signal.setDittoHeaders(DittoHeaders.of(
                                                    startedSpan.propagateContext(signal.getDittoHeaders())
                                            ))
                                    )
                            );
                            startedSpan.finish();
                        } catch (final IllegalAdaptableException e) {
                            logSignalBuildingFailure(logger.withCorrelationId(e)::info, e, cmdString);
                            final var failure = e.setDittoHeaders(DittoHeaders.newBuilder(e.getDittoHeaders())
                                    .origin(connectionCorrelationId)
                                    .build());
                            final var tracedFailure = traceSignalBuildingFailure(failure);
                            if (isResponseRequired(e)) {
                                result = Left.apply(tracedFailure);
                            } else {
                                result = Right.apply(Left.apply(NoOp.getInstance()));
                            }
                        } catch (final DittoRuntimeException e) {

                            // This is a client error usually; log at level DEBUG without stack trace.
                            logSignalBuildingFailure(logger.withCorrelationId(e)::debug, e, cmdString);
                            result = Left.apply(traceSignalBuildingFailure(e));
                        } catch (final Exception e) {
                            logSignalBuildingFailure(logger::warn, e, cmdString);
                            result = Left.apply(traceSignalBuildingFailure(GatewayInternalErrorException.newBuilder()
                                    .message(e.getMessage())
                                    .cause(e)
                                    .build()));
                        }
                    }
                    return result;
                });
    }

    private static void logSignalBuildingFailure(final BiConsumer<String, Object[]> logStatement,
            final Exception failure,
            final String signalJsonString) {

        logStatement.accept("Failed to build a Signal from <{}>; {}: {}", new Object[]{
                signalJsonString,
                failure.getClass().getSimpleName(),
                failure.getMessage()
        });
    }

    private static DittoRuntimeException traceSignalBuildingFailure(final DittoRuntimeException failure) {
        final var startedSpan = startTraceSpan(failure, SpanOperationName.of("gw.streaming.in.error"));
        startedSpan.tagAsFailed(failure);
        try {
            return failure.setDittoHeaders(DittoHeaders.of(startedSpan.propagateContext(failure.getDittoHeaders())));
        } finally {
            startedSpan.finish();
        }
    }

    private static StartedSpan startTraceSpan(final WithDittoHeaders withDittoHeaders, final SpanOperationName name) {
        final var preparedSpan = DittoTracing.newPreparedSpan(withDittoHeaders.getDittoHeaders(), name);
        return preparedSpan.start();
    }

    private Pair<Connect, Flow<DittoRuntimeException, Message, NotUsed>> createOutgoing(
            final JsonSchemaVersion version,
            final CharSequence connectionCorrelationId,
            final AuthorizationContext connectionAuthContext,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter,
            final HttpRequest request,
            final WebsocketConfig websocketConfig,
            @Nullable final SignalEnrichmentFacade signalEnrichmentFacade,
            final ThreadSafeDittoLogger logger) {

        final Optional<JsonWebToken> optJsonWebToken = extractJwtFromRequestIfPresent(request);

        final Source<SessionedJsonifiable, SupervisedStream.WithQueue> publisherSource =
                SupervisedStream.sourceQueue(websocketConfig.getPublisherBackpressureBufferSize());

        final Source<SessionedJsonifiable, Connect> sourceToPreMaterialize = publisherSource.mapMaterializedValue(
                        withQueue -> {
                            webSocketSupervisor.supervise(withQueue.getSupervisedStream(), connectionCorrelationId,
                                    additionalHeaders);
                            return new Connect(withQueue.getSourceQueue(), connectionCorrelationId, STREAMING_TYPE_WS,
                                    version, optJsonWebToken.map(JsonWebToken::getExpirationTime).orElse(null),
                                    readDeclaredAcknowledgementLabels(additionalHeaders), connectionAuthContext,
                                    wsKillSwitch);
                        })
                .recoverWithRetries(1, new PFBuilder<Throwable, Source<SessionedJsonifiable, NotUsed>>()
                        .match(GatewayWebsocketSessionAbortedException.class,
                                ex -> {
                                    logger.info("WebSocket connection aborted because of service restart!");
                                    return Source.empty();
                                })
                        .match(GatewayWebsocketSessionExpiredException.class,
                                ex -> {
                                    logger.info("WebSocket connection terminated because JWT expired!");
                                    return Source.empty();
                                })
                        .match(GatewayWebsocketSessionClosedException.class,
                                ex -> {
                                    logger.info(
                                            "WebSocket connection terminated because authorization context changed!");
                                    return Source.empty();
                                })
                        .match(DittoRuntimeException.class, ex -> Source.single(SessionedJsonifiable.error(ex)))
                        .build());

        final Pair<Connect, Source<SessionedJsonifiable, NotUsed>> sourcePair =
                sourceToPreMaterialize.preMaterialize(materializer);
        final Connect connect = sourcePair.first();
        final Source<SessionedJsonifiable, NotUsed> eventAndResponseSource = sourcePair.second();

        final Flow<DittoRuntimeException, SessionedJsonifiable, NotUsed> errorFlow =
                Flow.fromFunction(SessionedJsonifiable::error);

        final int signalEnrichmentParallelism = streamingConfig.getParallelism();
        final Flow<SessionedJsonifiable, Message, NotUsed> messageFlow =
                Flow.<SessionedJsonifiable>create()
                        .mapAsync(signalEnrichmentParallelism, postprocess(adapter, signalEnrichmentFacade, logger))
                        .mapConcat(x -> x)
                        .via(Flow.fromFunction(result -> {
                            logger.debug("Sending outgoing WebSocket message: {}", result);
                            return result;
                        }))
                        .via(outgoingMessageSniffer.toAsyncFlow(request))
                        .<Message>map(TextMessage::create)
                        .via(Flow.fromFunction(msg -> {
                            OUT_COUNTER.increment();
                            return msg;
                        }));

        return Pair.create(connect, joinOutgoingFlows(eventAndResponseSource, errorFlow, messageFlow));
    }

    private static Set<AcknowledgementLabel> readDeclaredAcknowledgementLabels(final DittoHeaders dittoHeaders) {
        return Optional.ofNullable(dittoHeaders.get(DittoHeaderDefinition.DECLARED_ACKS.getKey()))
                .map(JsonFactory::readFrom)
                .filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .map(JsonArray::stream)
                .orElseGet(Stream::empty)
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(AcknowledgementLabel::of)
                .collect(Collectors.toSet());
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

    private static <T> Graph<FanOutShape2<Either<T, Signal<?>>, Either<T, Signal<?>>, DittoRuntimeException>, NotUsed>
    getRateLimiter(final WebsocketConfig websocketConfig) {
        final Duration rateLimitInterval = websocketConfig.getThrottlingConfig().getInterval();
        final int throttlingLimit = websocketConfig.getThrottlingConfig().getLimit();
        final int messagesPerInterval =
                Math.max(throttlingLimit, (int) (throttlingLimit * websocketConfig.getThrottlingRejectionFactor()));
        if (websocketConfig.getThrottlingConfig().isEnabled()) {
            return LimitRateByRejection.of(rateLimitInterval, messagesPerInterval, either -> {
                final TooManyRequestsException.Builder builder =
                        TooManyRequestsException.newBuilder().retryAfter(rateLimitInterval);
                if (either.isRight()) {
                    builder.dittoHeaders(either.right().get().getDittoHeaders());
                }
                return builder.build();
            });
        } else {
            return Filter.multiplexByEither(Right::apply);
        }
    }

    private static DittoHeaders getInitialInternalHeaders(final JsonSchemaVersion jsonSchemaVersion,
            final AuthorizationContext connectionAuthContext,
            final CharSequence connectionCorrelationId) {

        return DittoHeaders.newBuilder()
                .schemaVersion(jsonSchemaVersion)
                .authorizationContext(connectionAuthContext)
                .correlationId(connectionCorrelationId) // for logging
                .origin(connectionCorrelationId)
                .build();
    }

    private static JsonifiableAdaptable getJsonifiableAdaptableOrThrow(final String messageJsonString,
            final DittoHeaders initialInternalHeaders) {

        if (messageJsonString.isEmpty()) {
            final RuntimeException cause = new IllegalArgumentException("Empty json.");
            throw new DittoJsonException(cause, initialInternalHeaders);
        }

        return wrapJsonRuntimeException(messageJsonString,
                DittoHeaders.empty(), // unused
                (s, unused) -> ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(s)));
    }

    private static Signal<?> buildSignal(final CharSequence connectionCorrelationId,
            final DittoHeaders initialInternalHeaders,
            final Adaptable adaptable,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter,
            final HeaderTranslator headerTranslator,
            final ThreadSafeDittoLogger logger) {

        final Signal<?> signal;
        try {
            signal = adapter.fromAdaptable(adaptable);
        } catch (final DittoRuntimeException e) {
            throw e.setDittoHeaders(e.getDittoHeaders().toBuilder().origin(connectionCorrelationId).build());
        }

        final ThreadSafeDittoLogger l = logger.withCorrelationId(signal);
        l.debug("WebSocket message has been converted to signal <{}>.", signal);
        final DittoHeaders signalHeaders = signal.getDittoHeaders();

        // add initial internal header values
        l.trace("Adding initialInternalHeaders: <{}>.", initialInternalHeaders);

        final DittoHeadersBuilder<?, ?> internalHeadersBuilder = DittoHeaders.newBuilder(initialInternalHeaders);

        // add headers given by parent route first so that protocol message may override them
        final Map<String, String> wellKnownAdditionalHeaders =
                headerTranslator.retainKnownHeaders(additionalHeaders);
        l.trace("Adding wellKnownAdditionalHeaders: <{}>.", wellKnownAdditionalHeaders);
        internalHeadersBuilder.putHeaders(wellKnownAdditionalHeaders);
        // add any headers from protocol adapter to internal headers
        l.trace("Adding signalHeaders: <{}>.", signalHeaders);
        internalHeadersBuilder.putHeaders(signalHeaders);
        // generate correlation ID if it is not set in protocol message
        if (signalHeaders.getCorrelationId().isEmpty()) {
            final String correlationId = UUID.randomUUID().toString();
            l.trace("Adding generated correlationId: <{}>.", correlationId);
            internalHeadersBuilder.correlationId(correlationId);
        }
        l.debug("Generated internalHeaders are: <{}>.", internalHeadersBuilder);

        return signal.setDittoHeaders(internalHeadersBuilder.build());
    }

    private static boolean isResponseRequired(final WithDittoHeaders withDittoHeaders) {
        final var dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.isResponseRequired();
    }

    private Function<SessionedJsonifiable, CompletionStage<Collection<String>>> postprocess(
            final ProtocolAdapter adapter, @Nullable final SignalEnrichmentFacade facade,
            final ThreadSafeDittoLogger logger) {

        return sessionedJsonifiable -> {
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable = sessionedJsonifiable.getJsonifiable();
            if (jsonifiable instanceof StreamingAck) {
                return CompletableFuture.completedFuture(
                        Collections.singletonList(streamingAckToString((StreamingAck) jsonifiable))
                );
            }

            final Adaptable adaptable = jsonifiableToAdaptable(jsonifiable, adapter);
            final CompletionStage<JsonObject> extraFuture = sessionedJsonifiable.retrieveExtraFields(facade);
            return extraFuture.<Collection<String>>thenApply(extra -> {
                if (matchesFilter(sessionedJsonifiable, extra)) {
                    return Collections.singletonList(toJsonStringWithExtra(adaptable, extra));
                }
                issuePotentialWeakAcknowledgements(sessionedJsonifiable);
                sessionedJsonifiable.finishSpan();
                return Collections.emptyList();
            }).exceptionally(error -> {
                sessionedJsonifiable.finishSpan();
                return WebSocketRoute.reportEnrichmentError(error, adapter, adaptable, logger);
            });
        };
    }

    private void issuePotentialWeakAcknowledgements(final SessionedJsonifiable sessionedJsonifiable) {
        sessionedJsonifiable.getSession().ifPresent(session -> {
            final DittoHeaders dittoHeaders = sessionedJsonifiable.getDittoHeaders();
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable =
                    sessionedJsonifiable.getJsonifiable();
            final ActorRef streamingSessionActor = session.getStreamingSessionActor();
            WithEntityId.getEntityIdOfType(EntityId.class, jsonifiable).ifPresent(entityId ->
                    dittoHeaders.getAcknowledgementRequests()
                            .stream()
                            .map(request -> weakAck(request.getLabel(), entityId, dittoHeaders))
                            .map(IncomingSignal::of)
                            .forEach(weakAck -> streamingSessionActor.tell(weakAck, ActorRef.noSender()))
            );
        });
    }

    private static Acknowledgement weakAck(final AcknowledgementLabel label,
            final EntityId entityId,
            final DittoHeaders dittoHeaders) {
        final JsonValue payload = JsonValue.of("Acknowledgement was issued automatically as weak ack, " +
                "because the signal is not relevant for the subscriber. Possible reasons are: " +
                "the subscriber did not subscribe for the signal type, " +
                "or the signal was dropped by a configured RQL filter.");
        return Acknowledgement.weak(label, entityId, dittoHeaders, payload);
    }

    private static Collection<String> reportEnrichmentError(final Throwable error,
            final ProtocolAdapter adapter,
            final Adaptable adaptable,
            final ThreadSafeDittoLogger logger) {

        final var errorToReport = DittoRuntimeException.asDittoRuntimeException(error, t ->
                SignalEnrichmentFailedException.newBuilder()
                        .dittoHeaders(adaptable.getDittoHeaders())
                        .cause(t)
                        .build());
        logger.withCorrelationId(adaptable.getDittoHeaders())
                .error("Signal enrichment failed due to: {}", error.getMessage(), errorToReport);

        final JsonifiableAdaptable errorAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(adapter.toAdaptable(ThingErrorResponse.of(
                        ThingId.of(adaptable.getTopicPath().getNamespace(), adaptable.getTopicPath().getEntityName()),
                        errorToReport,
                        adaptable.getDittoHeaders()
                )));
        return Collections.singletonList(errorAdaptable.toJsonString());
    }

    private static String toJsonStringWithExtra(final Adaptable adaptable, final JsonObject extra) {
        final Adaptable enrichedAdaptable = extra.isEmpty() ? adaptable : ProtocolFactory.setExtra(adaptable, extra);
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
                .map(session -> {
                    // evaluate to false if filter is present but does not match or has insufficient info to match
                    final Signal<?> signal = (Signal<?>) jsonifiable;
                    return session.matchesFilter(session.mergeThingWithExtra(signal, extra), signal);
                })
                .orElse(true);
    }

    private static String streamingAckToString(final StreamingAck streamingAck) {
        final StreamingType streamingType = streamingAck.getStreamingType();
        final boolean subscribed = streamingAck.isSubscribed();
        final String protocolMessage;
        switch (streamingType) {
            case EVENTS:
                protocolMessage = subscribed ? ProtocolMessageType.START_SEND_EVENTS.toString() :
                        ProtocolMessageType.STOP_SEND_EVENTS.toString();
                break;
            case MESSAGES:
                protocolMessage = subscribed ? ProtocolMessageType.START_SEND_MESSAGES.toString() :
                        ProtocolMessageType.STOP_SEND_MESSAGES.toString();
                break;
            case LIVE_COMMANDS:
                protocolMessage = subscribed ? ProtocolMessageType.START_SEND_LIVE_COMMANDS.toString() :
                        ProtocolMessageType.STOP_SEND_LIVE_COMMANDS.toString();
                break;
            case LIVE_EVENTS:
                protocolMessage = subscribed ? ProtocolMessageType.START_SEND_LIVE_EVENTS.toString() :
                        ProtocolMessageType.STOP_SEND_LIVE_EVENTS.toString();
                break;
            case POLICY_ANNOUNCEMENTS:
                protocolMessage = subscribed
                        ? ProtocolMessageType.START_SEND_POLICY_ANNOUNCEMENTS.toString()
                        : ProtocolMessageType.STOP_SEND_POLICY_ANNOUNCEMENTS.toString();
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
        } else if (jsonifiable instanceof DittoRuntimeException dittoRuntimeException) {
            final Signal<?> signal;
            if (jsonifiable instanceof PolicyException) {
                signal = buildPolicyErrorResponse(dittoRuntimeException);
            } else if (jsonifiable instanceof ThingSearchException) {
                signal = buildSearchErrorResponse(dittoRuntimeException);
            } else {
                signal = buildThingErrorResponse(dittoRuntimeException);
            }
            adaptable = adapter.toAdaptable(signal);
        } else {
            throw new IllegalArgumentException("Jsonifiable was neither Signal nor DittoRuntimeException: " +
                    jsonifiable.getClass().getSimpleName());
        }
        return adaptable;
    }

    private static ThingErrorResponse buildThingErrorResponse(final DittoRuntimeException dittoRuntimeException) {
        return ThingErrorResponse.of(dittoRuntimeException);
    }

    private static PolicyErrorResponse buildPolicyErrorResponse(final DittoRuntimeException dittoRuntimeException) {
        return PolicyErrorResponse.of(dittoRuntimeException);
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

}
