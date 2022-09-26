/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.mapping;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageBuilder;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.edge.service.placeholders.RequestPlaceholder;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageFormatInvalidException;
import org.eclipse.ditto.messages.model.MessageHeaderDefinition;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.MessagesModelFactory;
import org.eclipse.ditto.messages.model.signals.commands.MessageDeserializer;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.MessagePath;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;

/**
 * A message mapper implementation to convert between raw message payload and external message payload.
 */
public final class RawMessageMapper extends AbstractMessageMapper {

    private static final String PAYLOAD_MAPPER_ALIAS = "RawMessage";

    private static final JsonKey MESSAGES_JSON_KEY = JsonKey.of("messages");
    private static final String OUTGOING_CONTENT_TYPE_KEY = "outgoingContentType";
    private static final String INCOMING_MESSAGE_HEADERS = "incomingMessageHeaders";

    /**
     * Default outgoing content type is text/plain because binary requires base64 encoded string as payload,
     * which cannot be satisfied by all message commands and responses in the Ditto protocol, whereas
     * the text/plain content type can.
     */
    private static final ContentType DEFAULT_OUTGOING_CONTENT_TYPE =
            ContentType.of(ContentTypes.TEXT_PLAIN_UTF8.toString());

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();
    private static final RequestPlaceholder REQUEST_PLACEHOLDER = ConnectivityPlaceholders.newRequestPlaceholder();

    /**
     * Default incoming content type is binary.
     */
    private static final Map<String, String> DEFAULT_INCOMING_HEADERS = Map.of(
            DittoHeaderDefinition.CONTENT_TYPE.getKey(),
            getFromHeaderOrDefault(DittoHeaderDefinition.CONTENT_TYPE.getKey(),
                    ContentTypes.APPLICATION_OCTET_STREAM.toString()),
            MessageHeaderDefinition.DIRECTION.getKey(),
            getFromHeaderOrDefault(MessageHeaderDefinition.DIRECTION.getKey(), MessageDirection.TO.toString()),
            MessageHeaderDefinition.THING_ID.getKey(), asPlaceholder(MessageHeaderDefinition.THING_ID),
            MessageHeaderDefinition.SUBJECT.getKey(), asPlaceholder(MessageHeaderDefinition.SUBJECT),
            MessageHeaderDefinition.STATUS_CODE.getKey(), asPlaceholder(MessageHeaderDefinition.STATUS_CODE),
            MessageHeaderDefinition.FEATURE_ID.getKey(), asPlaceholder(MessageHeaderDefinition.FEATURE_ID)
    );

    private static final JsonObject DEFAULT_CONFIG = DittoMessageMapper.DEFAULT_OPTIONS.toBuilder()
            .set(OUTGOING_CONTENT_TYPE_KEY, DEFAULT_OUTGOING_CONTENT_TYPE.getValue())
            .set(INCOMING_MESSAGE_HEADERS, DEFAULT_INCOMING_HEADERS.entrySet()
                    .stream()
                    .map(entry -> JsonField.newInstance(entry.getKey(), JsonValue.of(entry.getValue())))
                    .collect(JsonCollectors.fieldsToObject()))
            .build();

    private final DittoMessageMapper dittoMessageMapper;

    /**
     * Fallback content-type for outgoing messages.
     * Content-type is the only relevant header for the outgoing direction.
     * Application headers should be defined in the target header mapping instead.
     */
    private ContentType fallbackOutgoingContentType = DEFAULT_OUTGOING_CONTENT_TYPE;

    /**
     * Fallback headers for the incoming direction.
     * One must rely on the headers to compute thing ID, feature ID, subject and message direction.
     * Only content-type is passed to the protocol adapter because it guides message deserialization.
     * Application headers should be defined in the source header mapping instead.
     */
    private Map<String, String> incomingMessageHeaders = DEFAULT_INCOMING_HEADERS;

    /**
     * Constructs a new instance of RawMessageMapper extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    RawMessageMapper(final ActorSystem actorSystem, final Config config) {
        super(actorSystem, config);
        dittoMessageMapper = new DittoMessageMapper(actorSystem, config);
    }

    private RawMessageMapper(final RawMessageMapper copyFromMapper) {
        super(copyFromMapper);
        this.dittoMessageMapper = copyFromMapper.dittoMessageMapper;
        this.fallbackOutgoingContentType = copyFromMapper.fallbackOutgoingContentType;
        this.incomingMessageHeaders = copyFromMapper.incomingMessageHeaders;
    }

    @Override
    public String getAlias() {
        return PAYLOAD_MAPPER_ALIAS;
    }

    @Override
    public boolean isConfigurationMandatory() {
        return false;
    }

    @Override
    public MessageMapper createNewMapperInstance() {
        return new RawMessageMapper(this);
    }

    @Override
    public List<Adaptable> map(final ExternalMessage externalMessage) {
        final Optional<MessageHeaders> messageHeadersOptional =
                evaluateIncomingMessageHeaders(externalMessage, incomingMessageHeaders);
        if (messageHeadersOptional.isEmpty()) {
            // message payload is a Ditto protocol message.
            return dittoMessageMapper.map(externalMessage);
        }
        final MessageHeaders messageHeaders = messageHeadersOptional.get();
        return List.of(ProtocolFactory.newAdaptableBuilder(toTopicPath(messageHeaders))
                .withPayload(toPayload(externalMessage, messageHeaders))
                .withHeaders(retainContentTypeOnly(messageHeaders))
                .build());
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(final ExternalMessage message) {
        return DittoHeaders.empty();
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        if (isMessageCommandOrResponse(adaptable)) {
            final ContentType contentType = adaptable.getDittoHeaders()
                    .getDittoContentType()
                    .orElse(fallbackOutgoingContentType);
            if (!contentType.isDittoProtocol()) {
                final ExternalMessageBuilder builder =
                        ExternalMessageFactory.newExternalMessageBuilder(
                                evaluateOutgoingMessageHeaders(adaptable, contentType))
                                .withInternalHeaders(adaptable.getDittoHeaders());
                adaptable.getPayload().getValue().ifPresent(payloadValue -> {
                    if (MessageDeserializer.shouldBeInterpretedAsTextOrJson(contentType)) {
                        builder.withText(toOutgoingText(payloadValue));
                    } else {
                        // binary payload only possible if payload is a base64-encoded string.
                        builder.withBytes(Optional.of(payloadValue)
                                .filter(JsonValue::isString)
                                .flatMap(value -> toOutgoingBinary(value.asString()))
                                .orElseThrow(() -> badContentType(contentType.getValue(), adaptable.getDittoHeaders()))
                        );
                    }
                });
                return List.of(builder.build());
            }
        }
        return dittoMessageMapper.map(adaptable);
    }

    @Override
    public JsonObject getDefaultOptions() {
        return DEFAULT_CONFIG;
    }

    @Override
    protected void doConfigure(final Connection connection, final MappingConfig mappingConfig, final MessageMapperConfiguration configuration) {
        dittoMessageMapper.doConfigure(connection, mappingConfig, configuration);
        fallbackOutgoingContentType = configuration.findProperty(OUTGOING_CONTENT_TYPE_KEY)
                .map(ContentType::of)
                .orElse(fallbackOutgoingContentType);
        configuration.findProperty(INCOMING_MESSAGE_HEADERS, JsonValue::isObject, JsonValue::asObject)
                .ifPresent(configuredHeaders -> {
                    if (!configuredHeaders.isEmpty()) {
                        incomingMessageHeaders = new HashMap<>(DEFAULT_INCOMING_HEADERS);
                        for (final JsonField field : configuredHeaders) {
                            incomingMessageHeaders.put(field.getKeyName(), field.getValue().formatAsString());
                        }
                        incomingMessageHeaders = Collections.unmodifiableMap(incomingMessageHeaders);
                    }
                });
    }

    private static String toOutgoingText(final JsonValue value) {
        return value.isString() ? value.asString() : value.toString();
    }

    private static Optional<byte[]> toOutgoingBinary(final String base64Encoded) {
        try {
            return Optional.of(Base64.getDecoder().decode(base64Encoded));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static MessageFormatInvalidException badContentType(final String contentType, final DittoHeaders headers) {
        return MessageFormatInvalidException.newBuilder(JsonFactory.nullArray())
                .message(String.format(
                        "Expect payload of a message of content-type <%s> to be a base64 encoded string.",
                        contentType))
                .description("Please make sure the message has the correct content-type.")
                .dittoHeaders(headers)
                .build();
    }

    private static boolean isMessageCommandOrResponse(final Adaptable adaptable) {
        final var topicPath = adaptable.getTopicPath();
        return topicPath.isCriterion(TopicPath.Criterion.MESSAGES);
    }

    private static String asPlaceholder(final MessageHeaderDefinition messageHeaderDefinition) {
        return String.format("{{header:%s}}", messageHeaderDefinition.getKey());
    }

    private static String getFromHeaderOrDefault(final String headerKey, final String defaultValue) {
        return "{{header:" + headerKey + "|fn:default('" + defaultValue + "')}}";
    }

    private static Map<String, String> evaluateOutgoingMessageHeaders(final Adaptable adaptable,
            @Nullable final ContentType contentType) {

        final var adaptablePayload = adaptable.getPayload();
        final MessagePath messagePath = adaptablePayload.getPath();
        final MessageDirection direction = messagePath.getDirection().orElseThrow();
        final TopicPath topicPath = adaptable.getTopicPath();
        final ThingId thingId = ThingId.of(topicPath.getNamespace(), topicPath.getEntityName());
        final String subject = topicPath.getSubject().orElseThrow();

        return MessagesModelFactory.newHeadersBuilder(direction, thingId, subject)
                .contentType(contentType)
                .httpStatus(adaptablePayload.getHttpStatus().orElse(null))
                .featureId(messagePath.getFeatureId().orElse(null))
                .build();
    }

    private static Optional<MessageHeaders> evaluateIncomingMessageHeaders(final ExternalMessage externalMessage,
            final Map<String, String> incomingMessageHeaders) {
        final ExpressionResolver resolver = PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object()),
                PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, externalMessage.getHeaders()),
                PlaceholderFactory.newPlaceholderResolver(REQUEST_PLACEHOLDER,
                        externalMessage.getAuthorizationContext().orElse(null))
        );
        final String contentTypeKey = DittoHeaderDefinition.CONTENT_TYPE.getKey();
        final String contentType = resolve(resolver, incomingMessageHeaders, contentTypeKey);
        if (contentType == null) {
            throw MessageFormatInvalidException.newBuilder(JsonArray.empty())
                    .message("The RawMessage mapper failed to resolve " + contentTypeKey +
                            " of an incoming message.")
                    .description("Please ensure that '" + contentTypeKey +
                            "' is defined and resolvable in the mapper configuration '" +
                            INCOMING_MESSAGE_HEADERS + "'.")
                    .build();
        } else if (ContentType.of(contentType).isDittoProtocol()) {
            // Ditto protocol message content type; do not attempt to construct message headers.
            return Optional.empty();
        }
        final Map<String, String> resolvedHeaders = new HashMap<>();
        incomingMessageHeaders.forEach((key, value) ->
                resolver.resolve(value).findFirst().ifPresent(resolvedHeaderValue ->
                        resolvedHeaders.put(key, resolvedHeaderValue)
                )
        );
        // throws IllegalArgumentException or SubjectInvalidException
        // if resolved headers are missing mandatory headers or the resolved subject is invalid.
        return Optional.of(MessagesModelFactory.newHeadersBuilder(resolvedHeaders).build());
    }

    @Nullable
    private static String resolve(final ExpressionResolver expressionResolver,
            final Map<String, String> incomingMessageHeaders,
            final String messageHeader) {

        final String placeholderExpression = incomingMessageHeaders.get(messageHeader);
        if (placeholderExpression != null) {
            return expressionResolver.resolve(placeholderExpression).findFirst().orElse(null);
        } else {
            return null;
        }
    }

    private static TopicPath toTopicPath(final MessageHeaders messageHeaders) {
        return ProtocolFactory.newTopicPathBuilder(messageHeaders.getEntityId())
                .live()
                .messages()
                .subject(messageHeaders.getSubject())
                .build();
    }

    private static Payload toPayload(final ExternalMessage externalMessage, final MessageHeaders messageHeaders) {
        final PayloadBuilder payloadBuilder = ProtocolFactory.newPayloadBuilder(toMessagePath(messageHeaders));
        messageHeaders.getHttpStatus().ifPresent(payloadBuilder::withStatus);
        getPayloadValue(externalMessage, messageHeaders.getDittoContentType().orElseThrow())
                .ifPresent(payloadBuilder::withValue);
        return payloadBuilder.build();
    }

    private static Optional<JsonValue> getPayloadValue(final ExternalMessage externalMessage,
            final ContentType contentType) {

        if (MessageDeserializer.shouldBeInterpretedAsTextOrJson(contentType)) {
            return externalMessage.getTextPayload()
                    .or(() -> externalMessage.getBytePayload().map(ByteBufferUtils::toUtf8String))
                    .map(textPayload -> contentType.isJson()
                            ? JsonFactory.readFrom(textPayload)
                            : JsonFactory.newValue(textPayload)
                    );
        } else {
            return externalMessage.getBytePayload()
                    .or(() -> externalMessage.getTextPayload().map(text -> ByteBuffer.wrap(text.getBytes())))
                    .map(bytePayload ->
                            JsonFactory.newValue(Base64.getEncoder().encodeToString(ByteBufferUtils.clone(bytePayload).array()))
                    );
        }
    }

    /**
     * Strip all message headers and retain content-type only.
     * The purpose is to guide the protocol adapter with the content type and let the protocol adapter fill
     * the rest of the message headers from topic path.
     *
     * @param messageHeaders the message headers evaluated from configuration.
     * @return the ditto headers for the incoming adaptable.
     */
    private static DittoHeaders retainContentTypeOnly(final MessageHeaders messageHeaders) {
        return DittoHeaders.newBuilder().contentType(messageHeaders.getContentType().orElse(null)).build();
    }

    private static JsonPointer toMessagePath(final MessageHeaders messageHeaders) {
        final JsonPointer jsonPointer = messageHeaders.getFeatureId()
                .map(s -> Thing.JsonFields.FEATURES.getPointer().addLeaf(JsonKey.of(s)))
                .orElseGet(JsonPointer::empty);
        return jsonPointer.addLeaf(MessagePath.directionToJsonKey(messageHeaders.getDirection()))
                .addLeaf(MESSAGES_JSON_KEY)
                .append(JsonPointer.of(messageHeaders.getSubject()));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", dittoMessageMapper=" + dittoMessageMapper +
                ", fallbackOutgoingContentType=" + fallbackOutgoingContentType +
                ", incomingMessageHeaders=" + incomingMessageHeaders +
                "]";
    }
}
