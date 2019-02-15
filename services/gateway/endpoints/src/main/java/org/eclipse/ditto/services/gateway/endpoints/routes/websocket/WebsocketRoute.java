/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.endpoints.routes.websocket;

import static akka.http.javadsl.server.Directives.complete;
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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.endpoints.utils.EventSniffer;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.ResponsePublished;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.gateway.streaming.actors.CommandSubscriber;
import org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.akka.LogUtil;
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
import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.UniformFanInShape;
import akka.stream.UniformFanOutShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Partition;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

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

    private final ActorRef streamingActor;
    private final int subscriberBackpressureQueueSize;
    private final int publisherBackpressureBufferSize;

    private final EventStream eventStream;

    private final EventSniffer<Message> incomingMessageSniffer;
    private final EventSniffer<Message> outgoingMessageSniffer;

    /**
     * Constructs the {@code /ws} route builder.
     *
     * @param streamingActor the {@link org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor} reference.
     * @param subscriberBackpressureQueueSize the max queue size of how many inflight Commands a single Websocket client
     * can have.
     * @param publisherBackpressureBufferSize the max buffer size of how many outstanding CommandResponses and Events a
     * single Websocket client can have - additionally incoming CommandResponses and Events are dropped if this size is
     * @param eventStream eventStream used to publish events within the actor system
     */
    public WebsocketRoute(final ActorRef streamingActor,
            final int subscriberBackpressureQueueSize,
            final int publisherBackpressureBufferSize,
            final EventStream eventStream) {
        this(streamingActor, subscriberBackpressureQueueSize, publisherBackpressureBufferSize, eventStream,
                EventSniffer.noOp(), EventSniffer.noOp());
    }

    private WebsocketRoute(final ActorRef streamingActor,
            final int subscriberBackpressureQueueSize,
            final int publisherBackpressureBufferSize,
            final EventStream eventStream,
            final EventSniffer<Message> incomingMessageSniffer,
            final EventSniffer<Message> outgoingMessageSniffer) {
        this.streamingActor = streamingActor;
        this.subscriberBackpressureQueueSize = subscriberBackpressureQueueSize;
        this.publisherBackpressureBufferSize = publisherBackpressureBufferSize;
        this.eventStream = eventStream;
        this.incomingMessageSniffer = incomingMessageSniffer;
        this.outgoingMessageSniffer = outgoingMessageSniffer;
    }

    /**
     * Create a copy of this object with message sniffers.
     *
     * @param incomingMessageSniffer sniffer of incoming messages.
     * @param outgoingMessageSniffer sniffer of outgoing messages.
     * @return a copy of this object with the message sniffers.
     */
    public WebsocketRoute withMessageSniffers(final EventSniffer<Message> incomingMessageSniffer,
            final EventSniffer<Message> outgoingMessageSniffer) {

        return new WebsocketRoute(streamingActor, subscriberBackpressureQueueSize, publisherBackpressureBufferSize,
                eventStream, incomingMessageSniffer, outgoingMessageSniffer);
    }

    /**
     * Builds the {@code /ws} route.
     *
     * @return the {@code /ws} route.
     */
    public Route buildWebsocketRoute(final Integer version, final String correlationId,
            final AuthorizationContext connectionAuthContext, final DittoHeaders additionalHeaders,
            final ProtocolAdapter chosenProtocolAdapter) {

        return extractUpgradeToWebSocket(upgradeToWebSocket -> extractRequest(request ->
                complete(
                        createWebsocket(upgradeToWebSocket, version, correlationId, connectionAuthContext,
                                additionalHeaders, chosenProtocolAdapter, request)
                )
        ));
    }

    private HttpResponse createWebsocket(final UpgradeToWebSocket upgradeToWebSocket, final Integer version,
            final String connectionCorrelationId, final AuthorizationContext authContext,
            final DittoHeaders additionalHeaders, final ProtocolAdapter adapter, final HttpRequest request) {

        LogUtil.logWithCorrelationId(LOGGER, connectionCorrelationId, logger ->
                logger.info("Creating WebSocket for connection authContext: <{}>", authContext));

        final Flow<Message, DittoRuntimeException, NotUsed> incoming =
                createIncoming(version, connectionCorrelationId, authContext, additionalHeaders, adapter, request);
        final Flow<DittoRuntimeException, Message, NotUsed> outgoing =
                createOutgoing(connectionCorrelationId, adapter, request);

        return upgradeToWebSocket.handleMessagesWith(incoming.via(outgoing));
    }

    private Flow<Message, DittoRuntimeException, NotUsed> createIncoming(final Integer version,
            final String connectionCorrelationId,
            final AuthorizationContext connectionAuthContext, final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter,
            final HttpRequest request) {

        final ProtocolMessageExtractor protocolMessageExtractor = new ProtocolMessageExtractor(connectionAuthContext,
                connectionCorrelationId);

        final Flow<Message, String, NotUsed> extractStringFromMessage = Flow.<Message>create()
                .via(incomingMessageSniffer.toAsyncFlow(request))
                .filter(Message::isText)
                .map(Message::asTextMessage)
                .map(textMsg -> {
                    if (textMsg.isStrict()) {
                        return Source.single(textMsg.getStrictText());
                    } else {
                        return textMsg.getStreamedText();
                    }
                })
                .flatMapConcat(textMsg -> textMsg.<String>fold("", (str1, str2) -> str1 + str2))
                .via(Flow.fromFunction(result -> {
                    LogUtil.logWithCorrelationId(LOGGER, connectionCorrelationId, logger ->
                            logger.debug("Received incoming WebSocket message: {}", result));
                    return result;
                }))
                .withAttributes(Attributes.createLogLevels(Logging.DebugLevel(), Logging.DebugLevel(),
                        Logging.WarningLevel()))
                .filter(strictText -> processProtocolMessage(protocolMessageExtractor, strictText));

        final Props commandSubscriberProps =
                CommandSubscriber.props(streamingActor, subscriberBackpressureQueueSize, eventStream);
        final Sink<Signal, ActorRef> commandSubscriber = Sink.actorSubscriber(commandSubscriberProps);

        final Flow<String, DittoRuntimeException, NotUsed> signalErrorFlow =
                buildSignalErrorFlow(commandSubscriber, version, connectionCorrelationId, connectionAuthContext,
                        additionalHeaders, adapter);

        return extractStringFromMessage.via(signalErrorFlow);
    }

    private boolean processProtocolMessage(final ProtocolMessageExtractor protocolMessageExtractor,
            final String protocolMessage) {
        final Object messageToTellStreamingActor = protocolMessageExtractor.apply(protocolMessage);
        if (messageToTellStreamingActor != null) {
            streamingActor.tell(messageToTellStreamingActor, null);
            return false;
        }
        // let all other messages pass:
        return true;
    }

    private Flow<DittoRuntimeException, Message, NotUsed> createOutgoing(final String connectionCorrelationId,
            final ProtocolAdapter adapter,
            final HttpRequest request) {

        final Source<Jsonifiable.WithPredicate<JsonObject, JsonField>, NotUsed> eventAndResponseSource =
                Source.<Jsonifiable.WithPredicate<JsonObject, JsonField>>actorPublisher(
                        EventAndResponsePublisher.props(publisherBackpressureBufferSize))
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
                        .<Message>map(TextMessage::create)
                        .via(outgoingMessageSniffer.toAsyncFlow(request));

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
                    final UniformFanInShape<T, T> merge = builder.add(Merge.<T>create(2));

                    builder.from(eventsAndResponses).toFanIn(merge);
                    builder.from(errors).toFanIn(merge);
                    builder.from(merge.out()).toInlet(messages.in());

                    return FlowShape.of(errors.in(), messages.out());
                }));
    }

    private Jsonifiable.WithPredicate<JsonObject, JsonField> publishResponsePublishedEvent(
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {
        if (jsonifiable instanceof CommandResponse) {
            // only create ResponsePublished for CommandResponses, not for Events with the same correlationId
            ((WithDittoHeaders) jsonifiable).getDittoHeaders()
                    .getCorrelationId()
                    .map(ResponsePublished::new)
                    .ifPresent(eventStream::publish);
        }
        return jsonifiable;
    }

    private Flow<String, DittoRuntimeException, NotUsed> buildSignalErrorFlow(
            final Sink<Signal, ActorRef> commandSubscriber,
            final Integer version,
            final String connectionCorrelationId,
            final AuthorizationContext connectionAuthContext,
            final DittoHeaders additionalHeaders,
            final ProtocolAdapter adapter) {

        final Flow<String, Object, NotUsed> resultOrErrorFlow =
                Flow.fromFunction(cmdString -> {
                    try {
                        return buildSignal(cmdString, version, connectionCorrelationId, connectionAuthContext,
                                additionalHeaders, adapter);
                    } catch (final Throwable throwable) {
                        // This is a client error usually; log at level INFO without stack trace.
                        LOGGER.info("Error building signal from <{}>: <{}:{}>", cmdString,
                                throwable.getClass().getCanonicalName(), throwable.getMessage());
                        return throwable;
                    }
                });

        final Graph<UniformFanOutShape<Object, Object>, NotUsed> signalFilterGraph =
                Partition.create(2, message -> message instanceof Signal ? 0 : 1);

        final Flow<Object, DittoRuntimeException, NotUsed> castErrorFlow =
                Flow.fromFunction(x -> x instanceof DittoRuntimeException
                        ? (DittoRuntimeException) x
                        : x instanceof Throwable
                        ? GatewayInternalErrorException.newBuilder().cause((Throwable) x).build()
                        : GatewayInternalErrorException.newBuilder().build());

        final Sink<Object, NotUsed> signalSinkGraph =
                Flow.fromFunction(Signal.class::cast).toMat(commandSubscriber, Keep.none());

        return connectSignalErrorFlow(resultOrErrorFlow, signalFilterGraph, signalSinkGraph, castErrorFlow);
    }

    @SuppressWarnings("unchecked")
    private static Flow<String, DittoRuntimeException, NotUsed> connectSignalErrorFlow(
            final Flow<String, Object, NotUsed> resultOrErrorFlow,
            final Graph<UniformFanOutShape<Object, Object>, NotUsed> signalFilterGraph,
            final Sink<Object, NotUsed> signalSinkGraph,
            final Flow<Object, DittoRuntimeException, NotUsed> castErrorFlow) {

        return Flow.fromGraph(GraphDSL.create(signalSinkGraph, (builder, signalSink) -> {
            final FlowShape<String, Object> resultOrError = builder.add(resultOrErrorFlow);
            final UniformFanOutShape<Object, Object> signalFilter = builder.add(signalFilterGraph);
            final FlowShape<Object, DittoRuntimeException> castError = builder.add(castErrorFlow);

            builder.from(resultOrError.out()).toInlet(signalFilter.in());
            builder.from(signalFilter.out(0)).to(signalSink);
            builder.from(signalFilter.out(1)).toInlet(castError.in());

            return FlowShape.of(resultOrError.in(), castError.out());
        }));
    }

    private Signal buildSignal(final String cmdString,
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
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
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
            final DittoHeaders enhancedHeaders = ((DittoRuntimeException) jsonifiable).getDittoHeaders().toBuilder()
                    .channel(channel.getName())
                    .build();
            ThingErrorResponse errorResponse;
            try {
                errorResponse = ThingErrorResponse.of(MessageHeaders.of(enhancedHeaders).getThingId(),
                        (DittoRuntimeException) jsonifiable, enhancedHeaders);
            } catch (final IllegalStateException | IllegalArgumentException | DittoRuntimeException e) {
                // thrown if headers did not contain the thing ID:
                errorResponse = ThingErrorResponse.of((DittoRuntimeException) jsonifiable, enhancedHeaders);
            }
            adaptable = adapter.toAdaptable(errorResponse, channel);
        } else {
            throw new IllegalArgumentException("Jsonifiable was neither Command nor CommandResponse nor"
                    + " Event nor DittoRuntimeException: " + jsonifiable.getClass().getSimpleName());
        }
        return adaptable;
    }

}
