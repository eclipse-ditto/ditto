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
package org.eclipse.ditto.protocoladapter.adaptables;

import static org.eclipse.ditto.model.messages.MessageHeaderDefinition.DIRECTION;
import static org.eclipse.ditto.model.messages.MessageHeaderDefinition.FEATURE_ID;
import static org.eclipse.ditto.model.messages.MessageHeaderDefinition.STATUS_CODE;
import static org.eclipse.ditto.model.messages.MessageHeaderDefinition.SUBJECT;
import static org.eclipse.ditto.model.messages.MessageHeaderDefinition.THING_ID;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.MessageHeadersBuilder;
import org.eclipse.ditto.model.messages.MessagesModelFactory;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.commands.messages.MessageCommandSizeValidator;
import org.eclipse.ditto.signals.commands.messages.MessageDeserializer;

/**
 * Provides helper methods to map from {@link Adaptable}s to MessageCommands or MessageCommandResponses.
 *
 * @param <T> the type of the mapped signals
 */
abstract class AbstractMessageMappingStrategies<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>>
        extends AbstractMappingStrategies<T> {

    private static final Base64.Decoder BASE_64_DECODER = Base64.getDecoder();

    protected AbstractMessageMappingStrategies(final Map<String, JsonifiableMapper<T>> mappingStrategies) {
        super(mappingStrategies);
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
     * <li>has no headers,</li>
     * <li>contains headers with a value that did not represent its appropriate Java type or</li>
     * <li>if the headers of {@code adaptable} did lack a mandatory header.</li>
     * </ul>
     * @throws org.eclipse.ditto.model.messages.SubjectInvalidException if {@code initialHeaders} contains an invalid
     * value for {@link MessageHeaderDefinition#SUBJECT}.
     * @throws org.eclipse.ditto.model.messages.MessagePayloadSizeTooLargeException if the message's payload is too
     * large
     */
    protected static <T> Message<T> messageFrom(final Adaptable adaptable) {
        final MessageHeaders messageHeaders = messageHeadersFrom(adaptable);

        final String contentType = String.valueOf(messageHeaders.get(DittoHeaderDefinition.CONTENT_TYPE.getKey()));
        final boolean shouldBeInterpretedAsText = shouldBeInterpretedAsText(contentType);

        final MessageBuilder<T> messageBuilder = MessagesModelFactory.newMessageBuilder(messageHeaders);
        final Optional<JsonValue> value = adaptable.getPayload().getValue();
        enforceMessageSizeLimit(messageHeaders, value);
        if (shouldBeInterpretedAsText) {
            if (isAnyText(contentType) && value.filter(JsonValue::isString).isPresent()) {
                messageBuilder.payload((T) value.get().asString());
            } else {
                value.ifPresent(jsonValue -> messageBuilder.payload((T) jsonValue));
            }
        } else {
            value.map(jsonValue -> jsonValue.isString() ? jsonValue.asString() : jsonValue.toString())
                    .map(payloadString -> payloadString.getBytes(StandardCharsets.UTF_8))
                    .ifPresent(bytes -> messageBuilder.rawPayload(ByteBuffer.wrap(tryToDecode(bytes))));
        }
        messageBuilder.extra(adaptable.getPayload().getExtra().orElse(null));
        return messageBuilder.build();
    }

    private static void enforceMessageSizeLimit(final MessageHeaders messageHeaders, final Optional<JsonValue> value) {
        MessageCommandSizeValidator.getInstance().ensureValidSize(
                () -> value.map(JsonValue::getUpperBoundForStringSize).orElse(Long.MAX_VALUE),
                () -> value.map(jsonValue -> jsonValue.toString().length()).orElse(0),
                () -> messageHeaders);
    }

    /**
     * Creates {@link MessageHeaders} from the passed {@link Adaptable}.
     *
     * @param adaptable the Adaptable to created the MessageHeaders from.
     * @return the MessageHeaders.
     * @throws NullPointerException if {@code adaptable} is {@code null}.
     * @throws IllegalArgumentException if {@code adaptable}
     * <ul>
     * <li>has no headers,</li>
     * <li>contains headers with a value that did not represent its appropriate Java type or</li>
     * <li>if the headers of {@code adaptable} did lack a mandatory header.</li>
     * </ul>
     * @throws org.eclipse.ditto.model.messages.SubjectInvalidException if {@code initialHeaders} contains an invalid
     * value for {@link MessageHeaderDefinition#SUBJECT}.
     */
    protected static MessageHeaders messageHeadersFrom(final Adaptable adaptable) {
        return adaptable.getHeaders()
                .map(headers -> {
                    final TopicPath topicPath = adaptable.getTopicPath();
                    final DittoHeadersBuilder dittoHeadersBuilder = headers.toBuilder();

                    // these headers are used to store message attributes of Message that are not fields.
                    // their content comes from elsewhere; overwrite message headers of the same names.
                    dittoHeadersBuilder.putHeader(THING_ID.getKey(),
                            topicPath.getNamespace() + ":" + topicPath.getId());
                    dittoHeadersBuilder.putHeader(SUBJECT.getKey(), topicPath.getSubject().orElse(""));
                    adaptable.getPayload().getPath().getDirection().ifPresent(direction ->
                            dittoHeadersBuilder.putHeader(DIRECTION.getKey(), direction.name()));
                    adaptable.getPayload().getPath().getFeatureId().ifPresent(featureId ->
                            dittoHeadersBuilder.putHeader(FEATURE_ID.getKey(), featureId));
                    adaptable.getPayload().getStatus().ifPresent(statusCode ->
                            dittoHeadersBuilder.putHeader(STATUS_CODE.getKey(), String.valueOf(statusCode.toInt())));
                    return dittoHeadersBuilder.build();
                })
                .map(MessagesModelFactory::newHeadersBuilder)
                .map(MessageHeadersBuilder::build)
                .orElseThrow(() -> new IllegalArgumentException("Adaptable did not have headers at all!"));
    }

    protected static byte[] tryToDecode(final byte[] bytes) {
        try {
            return BASE_64_DECODER.decode(bytes);
        } catch (final IllegalArgumentException e) {
            return bytes;
        }
    }

    private static boolean shouldBeInterpretedAsText(final String contentType) {
        return MessageDeserializer.shouldBeInterpretedAsTextOrJson(contentType);
    }

    /**
     * Get the status code from the adaptable payload.
     *
     * @throws NullPointerException if the Adaptable payload does not contain a status.
     */
    protected static HttpStatusCode statusCodeFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getStatus()
                .orElseThrow(() -> new NullPointerException("The message did not contain a status code."));
    }

    private static boolean isAnyText(final String contentType) {
        return MessageDeserializer.shouldBeInterpretedAsText(contentType);
    }

}
