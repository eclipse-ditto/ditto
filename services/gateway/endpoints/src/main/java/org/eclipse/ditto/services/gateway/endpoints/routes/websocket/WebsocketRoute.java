/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.routes.websocket;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.extractUpgradeToWebSocket;
import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.AdaptableBuilder;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.ResponsePublished;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.gateway.streaming.StreamingType;
import org.eclipse.ditto.services.gateway.streaming.actors.CommandSubscriber;
import org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.events.base.Event;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.event.EventStream;
import akka.event.Logging;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.model.ws.UpgradeToWebSocket;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.stream.Attributes;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Builder for creating Akka HTTP routes for {@code /ws}.
 */
public final class WebsocketRoute {

    private static final String START_SEND_EVENTS = "START-SEND-EVENTS";
    private static final String STOP_SEND_EVENTS = "STOP-SEND-EVENTS";

    private static final String START_SEND_MESSAGES = "START-SEND-MESSAGES";
    private static final String STOP_SEND_MESSAGES = "STOP-SEND-MESSAGES";

    private static final String START_SEND_LIVE_COMMANDS = "START-SEND-LIVE-COMMANDS";
    private static final String STOP_SEND_LIVE_COMMANDS = "STOP-SEND-LIVE-COMMANDS";

    private static final String START_SEND_LIVE_EVENTS = "START-SEND-LIVE-EVENTS";
    private static final String STOP_SEND_LIVE_EVENTS = "STOP-SEND-LIVE-EVENTS";

    /**
     * The backend sends the protocol message above suffixed by ":ACK" when the subscription was created. E.g.: {@code
     * START-SEND-EVENTS:ACK}
     */
    private static final String PROTOCOL_CMD_ACK_SUFFIX = ":ACK";

    private static final String STREAMING_TYPE_WS = "WS";

    private final ActorRef streamingActor;
    private final int subscriberBackpressureQueueSize;
    private final int publisherBackpressureBufferSize;

    private final ProtocolAdapter protocolAdapter;
    private final EventStream eventStream;

    /**
     * Constructs the {@code /ws} route builder.
     *  @param streamingActor the {@link org.eclipse.ditto.services.gateway.streaming.actors.StreamingActor} reference.
     * @param subscriberBackpressureQueueSize the max queue size of how many inflight Commands a single Websocket client
     * can have.
     * @param publisherBackpressureBufferSize the max buffer size of how many outstanding CommandResponses and Events a
     * single Websocket client can have - additionally incoming CommandResponses and Events are dropped if this size is
     * @param eventStream eventStream used to publish events within the actor system
     */
    public WebsocketRoute(final ActorRef streamingActor, final int subscriberBackpressureQueueSize,
            final int publisherBackpressureBufferSize, final ProtocolAdapter protocolAdapter,
            final EventStream eventStream) {
        this.streamingActor = streamingActor;
        this.subscriberBackpressureQueueSize = subscriberBackpressureQueueSize;
        this.publisherBackpressureBufferSize = publisherBackpressureBufferSize;
        this.protocolAdapter = protocolAdapter;
        this.eventStream = eventStream;
    }

    /**
     * Builds the {@code /ws} route.
     *
     * @return the {@code /ws} route.
     */
    public Route buildWebsocketRoute(final Integer version, final String correlationId,
            final AuthorizationContext connectionAuthContext) {
        return buildWebsocketRoute(version, correlationId, connectionAuthContext, DittoHeaders.empty());
    }

    /**
     * Builds the {@code /ws} route.
     *
     * @return the {@code /ws} route.
     */
    public Route buildWebsocketRoute(final Integer version, final String correlationId,
            final AuthorizationContext connectionAuthContext, final DittoHeaders additionalHeaders) {
        return extractUpgradeToWebSocket(upgradeToWebSocket ->
                complete(
                        createWebsocket(upgradeToWebSocket, version, correlationId, connectionAuthContext,
                                additionalHeaders)
                )
        );
    }

    private HttpResponse createWebsocket(final UpgradeToWebSocket upgradeToWebSocket, final Integer version,
            final String connectionCorrelationId, final AuthorizationContext connectionAuthContext,
            final DittoHeaders additionalHeaders) {
        // build Sink and Source in order to support rpc style patterns as well as server push:
        return upgradeToWebSocket.handleMessagesWith(
                createSink(version, connectionCorrelationId, connectionAuthContext, additionalHeaders),
                createSource(connectionCorrelationId));
    }

    private Sink<Message, NotUsed> createSink(final Integer version, final String connectionCorrelationId,
            final AuthorizationContext connectionAuthContext, final DittoHeaders additionalHeaders) {
        return Flow.<Message>create()
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
                .log("ws-incoming-msg")
                .withAttributes(Attributes.createLogLevels(Logging.DebugLevel(), Logging.DebugLevel(),
                        Logging.WarningLevel()))
                .filter(strictText -> processProtocolMessage(connectionAuthContext, connectionCorrelationId,
                        strictText))
                .map(buildSignal(version, connectionCorrelationId, connectionAuthContext, additionalHeaders))
                .to(Sink.actorSubscriber(
                        CommandSubscriber.props(streamingActor, subscriberBackpressureQueueSize, eventStream)));

    }

    private boolean processProtocolMessage(final AuthorizationContext authContext, final String connectionCorrelationId,
            final String protocolMessage) {
        final Object messageToTellStreamingActor;
        switch (protocolMessage) {
            case START_SEND_EVENTS:
                messageToTellStreamingActor =
                        new StartStreaming(StreamingType.EVENTS, connectionCorrelationId, authContext);
                break;
            case STOP_SEND_EVENTS:
                messageToTellStreamingActor = new StopStreaming(StreamingType.EVENTS, connectionCorrelationId);
                break;
            case START_SEND_MESSAGES:
                messageToTellStreamingActor =
                        new StartStreaming(StreamingType.MESSAGES, connectionCorrelationId, authContext);
                break;
            case STOP_SEND_MESSAGES:
                messageToTellStreamingActor = new StopStreaming(StreamingType.MESSAGES, connectionCorrelationId);
                break;
            case START_SEND_LIVE_COMMANDS:
                messageToTellStreamingActor = new StartStreaming(StreamingType.LIVE_COMMANDS, connectionCorrelationId,
                        authContext);
                break;
            case STOP_SEND_LIVE_COMMANDS:
                messageToTellStreamingActor = new StopStreaming(StreamingType.LIVE_COMMANDS, connectionCorrelationId);
                break;
            case START_SEND_LIVE_EVENTS:
                messageToTellStreamingActor =
                        new StartStreaming(StreamingType.LIVE_EVENTS, connectionCorrelationId, authContext);
                break;
            case STOP_SEND_LIVE_EVENTS:
                messageToTellStreamingActor = new StopStreaming(StreamingType.LIVE_EVENTS, connectionCorrelationId);
                break;
            default:
                messageToTellStreamingActor = null;
        }

        if (messageToTellStreamingActor != null) {
            streamingActor.tell(messageToTellStreamingActor, null);
            return false;
        }
        // let all other messages pass:
        return true;
    }

    private Source<Message, NotUsed> createSource(final String connectionCorrelationId) {
        return Source.<Jsonifiable.WithPredicate<JsonObject, JsonField>>actorPublisher(
                EventAndResponsePublisher.props(publisherBackpressureBufferSize))
                .mapMaterializedValue(actorRef -> {
                    streamingActor.tell(new Connect(actorRef, connectionCorrelationId, STREAMING_TYPE_WS), null);
                    return NotUsed.getInstance();
                })
                .map(this::publishResponsePublishedEvent)
                .map(this::jsonifiableToString)
                .map(TextMessage::create);
    }

    private Jsonifiable.WithPredicate<JsonObject, JsonField> publishResponsePublishedEvent(
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {
        if (jsonifiable instanceof WithDittoHeaders) {
            ((WithDittoHeaders) jsonifiable).getDittoHeaders()
                    .getCorrelationId()
                    .map(ResponsePublished::new)
                    .ifPresent(eventStream::publish);
        }
        return jsonifiable;
    }

    private Function<String, Signal> buildSignal(final Integer version, final String connectionCorrelationId,
            final AuthorizationContext connectionAuthContext, final DittoHeaders additionalHeaders) {
        return cmdString -> {
            final DittoHeadersBuilder dittoHeadersBuilder = DittoHeaders.newBuilder()
                    .schemaVersion(JsonSchemaVersion.forInt(version).orElse(JsonSchemaVersion.LATEST))
                    .authorizationContext(connectionAuthContext)
                    .correlationId(connectionCorrelationId)
                    .origin(connectionCorrelationId);

            if (cmdString.isEmpty()) {
                throw new DittoJsonException(new IllegalArgumentException("Empty json."), dittoHeadersBuilder.build());
            }

            final JsonObject jsonObject = wrapJsonRuntimeException(cmdString, dittoHeadersBuilder.build(),
                    (str, headers) -> JsonFactory.readFrom(str).asObject());
            final JsonifiableAdaptable jsonifiableAdaptable = wrapJsonRuntimeException(jsonObject,
                    DittoHeaders.newBuilder()
                            .schemaVersion(JsonSchemaVersion.forInt(version).orElse(JsonSchemaVersion.LATEST))
                            .authorizationContext(connectionAuthContext)
                            .correlationId(jsonObject.getValue(JsonifiableAdaptable.JsonFields.HEADERS.getPointer()
                                    .append(JsonPointer.of(DittoHeaderDefinition.CORRELATION_ID.getKey())))
                                    .filter(JsonValue::isString)
                                    .map(JsonValue::asString)
                                    .orElse(connectionCorrelationId)
                            )
                            .build(),
                    (jo, cmdHeaders) -> ProtocolFactory.jsonifiableAdaptableFromJson(jo));

            final DittoHeaders headers = jsonifiableAdaptable.getHeaders().orElse(ProtocolFactory.emptyHeaders());
            final String wsCorrelationId = headers.getCorrelationId()
                    .orElseGet(() -> UUID.randomUUID().toString());

            final JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.forInt(version)
                    .orElseThrow(() -> CommandNotSupportedException.newBuilder(version).build());

            final Map<String, String> allHeaders = new HashMap<>(headers);

            final DittoHeaders adjustedHeaders = DittoHeaders.newBuilder()
                    .authorizationContext(connectionAuthContext)
                    .schemaVersion(jsonSchemaVersion)
                    .correlationId(wsCorrelationId)
                    .origin(connectionCorrelationId)
                    .build();

            allHeaders.putAll(DittoHeaders.of(adjustedHeaders));
            allHeaders.putAll(additionalHeaders);

            final AdaptableBuilder adaptableBuilder = ProtocolFactory.newAdaptableBuilder(jsonifiableAdaptable)
                    .withHeaders(DittoHeaders.of(allHeaders))
                    .withPayload(jsonifiableAdaptable.getPayload());

            return protocolAdapter.fromAdaptable(adaptableBuilder.build());
        };
    }

    private String jsonifiableToString(final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {
        if (jsonifiable instanceof StreamingAck) {
            return streamingAckToString((StreamingAck) jsonifiable);
        }

        final Adaptable adaptable;
        if (jsonifiable instanceof Signal && isLiveSignal((Signal<?>) jsonifiable)) {
            adaptable = jsonifiableToAdaptable(jsonifiable, TopicPath.Channel.LIVE);
        } else {
            adaptable = jsonifiableToAdaptable(jsonifiable, TopicPath.Channel.TWIN);
        }

        final DittoHeaders dittoHeaders = ((WithDittoHeaders) jsonifiable).getDittoHeaders();
        // only choose relevant dittoHeaders for responses/events:
        final DittoHeadersBuilder dittoHeadersBuilder = DittoHeaders.newBuilder();
        // schemaVersion
        dittoHeaders.getSchemaVersion().ifPresent(dittoHeadersBuilder::schemaVersion);
        // source
        dittoHeaders.getSource().ifPresent(dittoHeadersBuilder::source);
        final DittoHeaders adjustedHeaders = dittoHeadersBuilder.build();

        final Map<String, String> allHeaders = new HashMap<>(adaptable.getHeaders().orElse(DittoHeaders.empty()));
        allHeaders.remove(DittoHeaderDefinition.ORIGIN.getKey());
        allHeaders.putAll(DittoHeaders.of(adjustedHeaders));

        final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
        final JsonObject jsonObject = jsonifiableAdaptable.toJson(DittoHeaders.of(allHeaders));
        return jsonObject.toString();
    }

    private static String streamingAckToString(final StreamingAck streamingAck) {
        final StreamingType streamingType = streamingAck.getStreamingType();
        final boolean subscribed = streamingAck.isSubscribed();
        final String protocolMessage;
        switch (streamingType) {
            case EVENTS:
                protocolMessage = subscribed ? START_SEND_EVENTS : STOP_SEND_EVENTS;
                break;
            case MESSAGES:
                protocolMessage = subscribed ? START_SEND_MESSAGES : STOP_SEND_MESSAGES;
                break;
            case LIVE_COMMANDS:
                protocolMessage = subscribed ? START_SEND_LIVE_COMMANDS : STOP_SEND_LIVE_COMMANDS;
                break;
            case LIVE_EVENTS:
                protocolMessage = subscribed ? START_SEND_LIVE_EVENTS : STOP_SEND_LIVE_EVENTS;
                break;
            default:
                throw new IllegalArgumentException("Unknown streamingType: " + streamingType);
        }
        return protocolMessage + PROTOCOL_CMD_ACK_SUFFIX;
    }

    private static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }

    private Adaptable jsonifiableToAdaptable(final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final TopicPath.Channel channel) {
        final Adaptable adaptable;
        if (jsonifiable instanceof Command) {
            adaptable = protocolAdapter.toAdaptable((Command) jsonifiable, channel);
        } else if (jsonifiable instanceof Event) {
            adaptable = protocolAdapter.toAdaptable((Event) jsonifiable, channel);
        } else if (jsonifiable instanceof CommandResponse) {
            adaptable = protocolAdapter.toAdaptable((CommandResponse) jsonifiable, channel);
        } else if (jsonifiable instanceof DittoRuntimeException) {
            final DittoHeaders enhancedHeaders = ((DittoRuntimeException) jsonifiable).getDittoHeaders().toBuilder()
                    .channel(channel.getName())
                    .build();
            final ThingErrorResponse errorResponse =
                    ThingErrorResponse.of((DittoRuntimeException) jsonifiable, enhancedHeaders);
            adaptable = protocolAdapter.toAdaptable(errorResponse, channel);
        } else {
            throw new IllegalArgumentException("Jsonifiable was neither Command nor CommandResponse nor"
                    + " Event nor DittoRuntimeException: " + jsonifiable.getClass().getSimpleName());
        }
        return adaptable;
    }

}
