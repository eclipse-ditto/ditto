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
import java.util.regex.Pattern;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.MessageHeadersBuilder;
import org.eclipse.ditto.model.messages.MessagesModelFactory;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;

/**
 * Common functionality for handling (e.g. decoding) {@link Message}s used in {@link MessageCommandAdapter} and
 * {@link MessageCommandResponseAdapter}.
 */
final class MessageAdaptableHelper {

    private static final Base64.Decoder BASE_64_DECODER = Base64.getDecoder();
    private static final Pattern CHARSET_PATTERN = Pattern.compile(";.?charset=");

    /**
     * Creates an {@link Adaptable} from the passed {@link Message} and its related arguments.
     *
     * @param channel the Channel.
     * @param thingId the Thing ID.
     * @param messageCommandJson the JSON representation of the MessageCommand.
     * @param message the Message to created the Adaptable from.
     * @param dittoHeaders the used DittoHeaders.
     * @return the Adaptable.
     */
    static Adaptable adaptableFrom(final TopicPath.Channel channel,
            final String thingId,
            final JsonObject messageCommandJson,
            final Message<?> message,
            final DittoHeaders dittoHeaders) {

        final TopicPathBuilder topicPathBuilder = DittoProtocolAdapter.newTopicPathBuilder(thingId);

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

        final PayloadBuilder payloadBuilder = Payload.newBuilder();

        messageCommandJson.getValue(messagePointer.append(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD.getPointer()))
                .map(p -> messageCommandHeadersJsonObject.getValue(MessageHeaderDefinition.CONTENT_TYPE.getKey())
                        .filter(JsonValue::isString)
                        .map(JsonValue::asString)
                        .filter(MessageAdaptableHelper::shouldBeInterpretedAsText)
                        .map(MessageAdaptableHelper::determineCharset)
                        .map(charset -> JsonValue.newInstance(
                                new String(BASE_64_DECODER.decode(p.asString()), charset)))
                        .orElse(p))
                .ifPresent(payloadBuilder::withValue);

        message.getStatusCode().ifPresent(payloadBuilder::withStatus);

        return Adaptable.newBuilder(messagesTopicPathBuilder.build())
                .withPayload(payloadBuilder.build())
                .withHeaders(allHeadersBuilder.build())
                .build();
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
        final MessageHeaders messageHeaders = adaptable.getHeaders()
                .map(MessagesModelFactory::newHeadersBuilder)
                .map(MessageHeadersBuilder::build)
                .orElseThrow(() -> new IllegalArgumentException("Adaptable did not have headers at all!"));

        final String contentType = String.valueOf(messageHeaders.get(MessageHeaderDefinition.CONTENT_TYPE.getKey()));
        final boolean isPlainText = shouldBeInterpretedAsText(contentType);
        final Charset charset = isPlainText ? determineCharset(contentType) : StandardCharsets.UTF_8;

        final byte[] messagePayloadBytes = adaptable.getPayload()
                .getValue()
                .map(jsonValue -> jsonValue.isString() ? jsonValue.asString() : jsonValue.toString())
                .map(payloadString -> payloadString.getBytes(charset))
                .orElseThrow(() -> JsonParseException.newBuilder().build());

        return MessagesModelFactory.<T>newMessageBuilder(messageHeaders)
                .rawPayload(ByteBuffer.wrap(isPlainText ? messagePayloadBytes : tryToDecode(messagePayloadBytes)))
                .build();
    }

    private static byte[] tryToDecode(final byte[] bytes) {
        try {
            return BASE_64_DECODER.decode(bytes);
        } catch (final IllegalArgumentException e) {
            return bytes;
        }
    }

    private static boolean shouldBeInterpretedAsText(final String contentType) {
        return contentType.startsWith("text/plain") || contentType.startsWith("application/json");
    }

    private static Charset determineCharset(final CharSequence contentType) {
        final String[] withCharset = CHARSET_PATTERN.split(contentType, 2);
        if (2 == withCharset.length && Charset.isSupported(withCharset[1])) {
            return Charset.forName(withCharset[1]);
        }
        return StandardCharsets.UTF_8;
    }

}
