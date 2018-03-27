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
package org.eclipse.ditto.protocoladapter;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.MessageHeadersBuilder;
import org.eclipse.ditto.model.messages.MessagesModelFactory;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;

/**
 * Common functionality for handling (e.g. decoding) {@link Message}s used in {@link MessageCommandAdapter} and
 * {@link MessageCommandResponseAdapter}.
 */
final class MessageAdaptableHelper {

    private static final Base64.Decoder BASE_64_DECODER = Base64.getDecoder();
    private static final Pattern CHARSET_PATTERN = Pattern.compile(";.?charset=");

    private static final String TEXT_PLAIN = "text/plain";
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    private MessageAdaptableHelper() {
        throw new AssertionError();
    }

    /**
     * Creates an {@link Adaptable} from the passed {@link Message} and its related arguments.
     *
     * @param channel the Channel.
     * @param thingId the Thing ID.
     * @param messageCommandJson the JSON representation of the MessageCommand.
     * @param resourcePath the resource Path of the MessageCommand.
     * @param message the Message to created the Adaptable from.
     * @param dittoHeaders the used DittoHeaders.
     * @return the Adaptable.
     */
    static Adaptable adaptableFrom(final TopicPath.Channel channel,
            final String thingId,
            final JsonObject messageCommandJson,
            final JsonPointer resourcePath,
            final Message<?> message,
            final DittoHeaders dittoHeaders) {

        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(thingId);

        final MessagesTopicPathBuilder messagesTopicPathBuilder;
        if (channel == TopicPath.Channel.LIVE) {
            messagesTopicPathBuilder = topicPathBuilder.live().messages();
        } else {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }

        messagesTopicPathBuilder.subject(message.getSubject());

        final JsonPointer messagePointer = MessageCommand.JsonFields.JSON_MESSAGE.getPointer();
        final JsonPointer headersJsonPointer = messagePointer.append(MessageCommand.JsonFields.JSON_MESSAGE_HEADERS
                .getPointer());
        final JsonObject messageCommandHeadersJsonObject = messageCommandJson.getValue(headersJsonPointer)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElseGet(JsonFactory::newObject);

        final DittoHeadersBuilder allHeadersBuilder = DittoHeaders.newBuilder(messageCommandHeadersJsonObject);
        allHeadersBuilder.putHeaders(dittoHeaders);

        final PayloadBuilder payloadBuilder = Payload.newBuilder(resourcePath);

        messageCommandJson.getValue(messagePointer.append(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD.getPointer()))
                .map(p -> messageCommandHeadersJsonObject.getValue(DittoHeaderDefinition.CONTENT_TYPE.getKey())
                        .filter(JsonValue::isString)
                        .map(JsonValue::asString)
                        .map(contentType -> {
                            if (MessageAdaptableHelper.shouldBeInterpretedAsText(contentType) ||
                                    MessageAdaptableHelper.shouldBeInterpretedAsBinary(contentType)) {
                                return p;
                            } else {
                                return JsonValue.of(
                                        new String(BASE_64_DECODER.decode(p.asString()),
                                                MessageAdaptableHelper.determineCharset(contentType)));
                            }
                        })
                        .orElse(p))
                .ifPresent(payloadBuilder::withValue);

        message.getStatusCode().ifPresent(payloadBuilder::withStatus);

        messageCommandJson.getValue(CommandResponse.JsonFields.STATUS).ifPresent(payloadBuilder::withStatus);

        return Adaptable.newBuilder(messagesTopicPathBuilder.build())
                .withPayload(payloadBuilder.build())
                .withHeaders(allHeadersBuilder.build()).build();
    }

    /**
     * Creates a {@link Message} from the passed {@link Adaptable}.
     *
     * @param adaptable the Adaptable to created the Message from.
     * @param <T> the type of the Message's payload.
     * @return the Message.
     * @throws NullPointerException if {@code adaptable} is {@code null}.
     * @throws IllegalArgumentException if {@code adaptable}
     * <ul>
     *     <li>has no headers,</li>
     *     <li>contains headers with a value that did not represent its appropriate Java type or</li>
     *     <li>if the headers of {@code adaptable} did lack a mandatory header.</li>
     * </ul>
     * @throws org.eclipse.ditto.model.messages.SubjectInvalidException if {@code initialHeaders} contains an invalid
     * value for {@link MessageHeaderDefinition#SUBJECT}.
     */
    static <T> Message<T> messageFrom(final Adaptable adaptable) {
        final MessageHeaders messageHeaders = messageHeadersFrom(adaptable);

        final String contentType = String.valueOf(messageHeaders.get(DittoHeaderDefinition.CONTENT_TYPE.getKey()));
        final boolean shouldBeInterpretedAsText = shouldBeInterpretedAsText(contentType);
        final Charset charset = shouldBeInterpretedAsText ? determineCharset(contentType) : StandardCharsets.UTF_8;

        final MessageBuilder<T> messageBuilder = MessagesModelFactory.<T>newMessageBuilder(messageHeaders);
        final Optional<JsonValue> value = adaptable.getPayload().getValue();
        if (shouldBeInterpretedAsText) {
            if (isPlainText(contentType) && value.filter(JsonValue::isString).isPresent()) {
                messageBuilder.payload((T) value.get().asString());
            } else {
                value.ifPresent(jsonValue -> messageBuilder.payload((T) jsonValue));
            }
        } else {
            value.map(jsonValue -> jsonValue.isString() ? jsonValue.asString() : jsonValue.toString())
                    .map(payloadString -> payloadString.getBytes(charset))
                    .ifPresent(bytes -> messageBuilder.rawPayload(ByteBuffer.wrap(tryToDecode(bytes))));
        }
        return messageBuilder.build();
    }

    /**
     * Creates {@link MessageHeaders} from the passed {@link Adaptable}.
     *
     * @param adaptable the Adaptable to created the MessageHeaders from.
     * @return the MessageHeaders.
     * @throws NullPointerException if {@code adaptable} is {@code null}.
     * @throws IllegalArgumentException if {@code adaptable}
     * <ul>
     *     <li>has no headers,</li>
     *     <li>contains headers with a value that did not represent its appropriate Java type or</li>
     *     <li>if the headers of {@code adaptable} did lack a mandatory header.</li>
     * </ul>
     * @throws org.eclipse.ditto.model.messages.SubjectInvalidException if {@code initialHeaders} contains an invalid
     * value for {@link MessageHeaderDefinition#SUBJECT}.
     */
    static MessageHeaders messageHeadersFrom(final Adaptable adaptable) {
        return adaptable.getHeaders()
                .map(headers -> {
                    final TopicPath topicPath = adaptable.getTopicPath();
                    final DittoHeadersBuilder dittoHeadersBuilder = headers.toBuilder();
                    if (!headers.containsKey(MessageHeaderDefinition.THING_ID.getKey())) {
                        dittoHeadersBuilder.putHeader(MessageHeaderDefinition.THING_ID.getKey(),
                                topicPath.getNamespace() + ":" + topicPath.getId());
                    }
                    if (!headers.containsKey(MessageHeaderDefinition.SUBJECT.getKey())) {
                        dittoHeadersBuilder.putHeader(MessageHeaderDefinition.SUBJECT.getKey(),
                                topicPath.getSubject().orElse(""));
                    }
                    return dittoHeadersBuilder.build();
                })
                .map(MessagesModelFactory::newHeadersBuilder)
                .map(MessageHeadersBuilder::build)
                .orElseThrow(() -> new IllegalArgumentException("Adaptable did not have headers at all!"));
    }

    private static byte[] tryToDecode(final byte[] bytes) {
        try {
            return BASE_64_DECODER.decode(bytes);
        } catch (final IllegalArgumentException e) {
            return bytes;
        }
    }

    private static boolean shouldBeInterpretedAsText(final String contentType) {
        return isPlainText(contentType) || contentType.startsWith(APPLICATION_JSON) ||
                DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE.equalsIgnoreCase(contentType);
    }

    private static boolean shouldBeInterpretedAsBinary(final String contentType) {
        return contentType.startsWith(APPLICATION_OCTET_STREAM);
    }

    private static boolean isPlainText(final String contentType) {
        return contentType.startsWith(TEXT_PLAIN);
    }

    private static Charset determineCharset(final CharSequence contentType) {
        final String[] withCharset = CHARSET_PATTERN.split(contentType, 2);
        if (2 == withCharset.length && Charset.isSupported(withCharset[1])) {
            return Charset.forName(withCharset[1]);
        }
        return StandardCharsets.UTF_8;
    }

}
