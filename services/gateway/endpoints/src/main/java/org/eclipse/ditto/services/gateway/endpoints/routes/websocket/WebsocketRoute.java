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

import static akka.http.javadsl.server.Directives.completeWithFuture;
import static akka.http.javadsl.server.Directives.extractRequest;
import static akka.http.javadsl.server.Directives.extractUpgradeToWebSocket;
import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.START_SEND_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.START_SEND_LIVE_COMMANDS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.START_SEND_LIVE_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.START_SEND_MESSAGES;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.STOP_SEND_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.STOP_SEND_LIVE_COMMANDS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.STOP_SEND_LIVE_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.STOP_SEND_MESSAGES;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.TooManyRequestsException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.endpoints.utils.EventSniffer;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.ResponsePublished;
import org.eclipse.ditto.services.gateway.streaming.StreamControlMessage;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.gateway.streaming.WebsocketConfig;
import org.eclipse.ditto.services.gateway.streaming.actors.CommandSubscriber;
import org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher;
import org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.LimitRateByRejection;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
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
public final class WebsocketRoute {

    /**
     * The backend sends the protocol message above suffixed by ":ACK" when the subscription was created. E.g.: {@code
     * START-SEND-EVENTS:ACK}
     */
    private static final String PROTOCOL_CMD_ACK_SUFFIX = ":ACK";

    private static final String STREAMING_TYPE_WS = "WS";

    private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketRoute.class);

    private static final Duration CONFIG_ASK_TIMEOUT = Duration.ofSeconds(5L);

    private static final Counter IN_COUNTER = DittoMetrics.counter("streaming_messages")
            .tag("type", "ws")
            .tag("direction", "in");

    private static final Counter OUT_COUNTER = DittoMetrics.counter("streaming_messages")
            .tag("type", "ws")
            .tag("direction", "out");

    private static final Counter DROPPED_COUNTER = DittoMetrics.counter("streaming_messages")
            .tag("type", "ws")
            .tag("direction", "dropped");

    private final ActorRef streamingActor;
    private final EventStream eventStream;

    private final EventSniffer<String> incomingMessageSniffer;
    private final EventSniffer<String> outgoingMessageSniffer;

    private WebsocketRoute(final ActorRef streamingActor,
            final EventStream eventStream,
            final EventSniffer<String> incomingMessageSniffer,
            final EventSniffer<String> outgoingMessageSniffer) {
        this.streamingActor = streamingActor;
        this.eventStream = eventStream;
        this.incomingMessageSniffer = incomingMessageSniffer;
        this.outgoingMessageSniffer = outgoingMessageSniffer;
    }

    /**
     * Constructs the {@code /ws} route builder.
     *
     * @param streamingActor the {@link org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor} reference.
     * @param eventStream eventStream used to publish events within the actor system
     */
    public WebsocketRoute(final ActorRef streamingActor,
            final EventStream eventStream) {

        this(streamingActor, eventStream, EventSniffer.noOp(), EventSniffer.noOp());
    }

    /**
     * Create a copy of this object with message sniffers.
     *
     * @param incomingMessageSniffer sniffer of incoming messages.
     * @param outgoingMessageSniffer sniffer of outgoing messages.
     * @return a copy of this object with the message sniffers.
     */
    public WebsocketRoute withMessageSniffers(final EventSniffer<String> incomingMessageSniffer,
            final EventSniffer<String> outgoingMessageSniffer) {

        return new WebsocketRoute(streamingActor, eventStream, incomingMessageSniffer,
                outgoingMessageSniffer);
    }

    /**
     * Builds the {@code /ws} route.
     *
     * @return the {@code /ws} route.
     */
    public Route buildWebsocketRoute(final Integer version,
            final String correlationId,
            final AuthorizationContext connectionAuthContext,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter chosenProtocolAdapter) {

        return extractUpgradeToWebSocket(upgradeToWebSocket -> extractRequest(request ->
                completeWithFuture(
                        createWebsocket(upgradeToWebSocket, version, correlationId, connectionAuthContext,
                                additionalHeaders, chosenProtocolAdapter, request)
                )
        ));
    }

    private CompletionStage<WebsocketConfig> retrieveWebsocketConfig() {
        return Patterns.ask(streamingActor, StreamingActor.Control.RETRIEVE_WEBSOCKET_CONFIG, CONFIG_ASK_TIMEOUT)
                .thenApply(reply -> (WebsocketConfig) reply); // fail future with ClassCastException on type error
    }

    private CompletionStage<HttpResponse> createWebsocket(final UpgradeToWebSocket upgradeToWebSocket,
            final Integer version,
            final String connectionCorrelationId,
            final AuthorizationContext authContext,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter,
            final HttpRequest request) {

        LogUtil.logWithCorrelationId(LOGGER, connectionCorrelationId, logger ->
                logger.info("Creating WebSocket for connection authContext: <{}>", authContext));

        return retrieveWebsocketConfig().thenApply(websocketConfig -> {
            final Flow<Message, DittoRuntimeException, NotUsed> incoming =
                    createIncoming(version, connectionCorrelationId, authContext, additionalHeaders, adapter, request,
                            websocketConfig);
            final Flow<DittoRuntimeException, Message, NotUsed> outgoing =
                    createOutgoing(connectionCorrelationId, adapter, request, websocketConfig);

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
                    builder.add(getStrictifyFlow(request, connectionCorrelationId));

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

            final UniformFanInShape<DittoRuntimeException, DittoRuntimeException> merge =
                    builder.add(Merge.create(2, true));

            builder.from(strictify.out()).toInlet(select.in());
            builder.from(select.out0()).toInlet(rateLimiter.in());
            builder.from(select.out1()).toFanIn(merge);
            builder.from(rateLimiter.out0()).to(sink);
            builder.from(rateLimiter.out1()).via(droppedCounter).toFanIn(merge);

            return FlowShape.of(strictify.in(), merge.out());
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
                    LogUtil.logWithCorrelationId(LOGGER, correlationId, logger ->
                            logger.debug("Received incoming WebSocket message: {}", result));
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
            final StreamControlMessage streamControlMessage = protocolMessageExtractor.apply(cmdString);
            if (streamControlMessage != null) {
                return Right.apply(Left.apply(streamControlMessage));
            } else {
                try {
                    final Signal signal =
                            buildSignal(cmdString, version, connectionCorrelationId, connectionAuthContext,
                                    additionalHeaders, adapter);
                    return Right.apply(Right.apply(signal));
                } catch (final Throwable throwable) {
                    // This is a client error usually; log at level DEBUG without stack trace.
                    LOGGER.debug("Error building signal from <{}>: <{}>", cmdString, throwable);
                    final DittoRuntimeException dittoRuntimeException =
                            throwable instanceof DittoRuntimeException
                                    ? (DittoRuntimeException) throwable
                                    : GatewayInternalErrorException.newBuilder().cause(throwable).build();
                    return Left.apply(dittoRuntimeException);
                }
            }
        });
    }

    private Flow<DittoRuntimeException, Message, NotUsed> createOutgoing(final String connectionCorrelationId,
            final ProtocolAdapter adapter, final HttpRequest request, final WebsocketConfig websocketConfig) {

        final Source<Jsonifiable.WithPredicate<JsonObject, JsonField>, NotUsed> eventAndResponseSource =
                Source.<Jsonifiable.WithPredicate<JsonObject, JsonField>>actorPublisher(
                        EventAndResponsePublisher.props(websocketConfig.getPublisherBackpressureBufferSize()))
                        .mapMaterializedValue(actorRef -> {
                            streamingActor.tell(new Connect(actorRef, connectionCorrelationId, STREAMING_TYPE_WS),
                                    null);
                            return NotUsed.getInstance();
                        })
                        .map(this::publishResponsePublishedEvent);

        final Flow<DittoRuntimeException, Jsonifiable.WithPredicate<JsonObject, JsonField>, NotUsed> errorFlow =
                Flow.fromFunction(x -> x);

        final Flow<Jsonifiable.WithPredicate<JsonObject, JsonField>, Message, NotUsed> messageFlow =
                Flow.fromFunction(jsonifiableToString(adapter))
                        .via(Flow.fromFunction(result -> {
                            LogUtil.logWithCorrelationId(LOGGER, connectionCorrelationId, logger ->
                                    logger.debug("Sending outgoing WebSocket message: {}", result));
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

    private Jsonifiable.WithPredicate<JsonObject, JsonField> publishResponsePublishedEvent(
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {

        if (jsonifiable instanceof CommandResponse || jsonifiable instanceof DittoRuntimeException) {
            // only create ResponsePublished for CommandResponses and DittoRuntimeExceptions
            // not for Events with the same correlation ID
            ((WithDittoHeaders) jsonifiable).getDittoHeaders()
                    .getCorrelationId()
                    .map(ResponsePublished::new)
                    .ifPresent(eventStream::publish);
        }
        return jsonifiable;
    }

    private static <T> Graph<FanOutShape2<Either<T, Signal>, Either<T, Signal>, DittoRuntimeException>, NotUsed>
    getRateLimiter(final WebsocketConfig websocketConfig) {
        final Duration rateLimitInterval = websocketConfig.getThrottlingConfig().getInterval();
        final int messagesPerInterval = websocketConfig.getThrottlingConfig().getLimit();
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

        LogUtil.logWithCorrelationId(LOGGER, connectionCorrelationId, logger -> {
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
        });

        return signal.setDittoHeaders(internalHeadersBuilder.build());
    }

    private static Function<Jsonifiable.WithPredicate<JsonObject, JsonField>, String> jsonifiableToString(
            final ProtocolAdapter adapter) {
        return jsonifiable -> {
            if (jsonifiable instanceof StreamingAck) {
                return streamingAckToString((StreamingAck) jsonifiable);
            }

            final Adaptable adaptable;
            if (jsonifiable instanceof WithDittoHeaders
                    && ((WithDittoHeaders) jsonifiable).getDittoHeaders().getChannel().isPresent()) {
                // if channel was present in headers, use that one:
                final TopicPath.Channel channel =
                        TopicPath.Channel.forName(((WithDittoHeaders) jsonifiable).getDittoHeaders().getChannel().get())
                                .orElse(TopicPath.Channel.TWIN);
                adaptable = jsonifiableToAdaptable(jsonifiable, channel, adapter);
            } else if (jsonifiable instanceof Signal && isLiveSignal((Signal<?>) jsonifiable)) {
                adaptable = jsonifiableToAdaptable(jsonifiable, TopicPath.Channel.LIVE, adapter);
            } else {
                adaptable = jsonifiableToAdaptable(jsonifiable, TopicPath.Channel.TWIN, adapter);
            }

            final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
            return jsonifiableAdaptable.toJsonString();
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

}
