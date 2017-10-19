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
package org.eclipse.ditto.model.messages;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.AbstractDittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * A mutable builder with a fluent API for an immutable {@link MessageHeaders} object.
 */
@NotThreadSafe
public final class MessageHeadersBuilder extends AbstractDittoHeadersBuilder<MessageHeadersBuilder, MessageHeaders> {

    private static final Set<MessageHeaderDefinition> MANDATORY_HEADERS = Collections.unmodifiableSet(
            EnumSet.of(MessageHeaderDefinition.DIRECTION, MessageHeaderDefinition.THING_ID,
                    MessageHeaderDefinition.SUBJECT));

    private MessageHeadersBuilder(final Map<String, String> headers) {
        super(headers, Arrays.asList(MessageHeaderDefinition.values()), MessageHeadersBuilder.class);
    }

    /**
     * Returns a new instance of {@code MessageHeadersBuilder}.
     *
     * @param direction the direction of the message.
     * @param thingId the thing ID of the message.
     * @param subject the subject of the message.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} or {@code subject} is empty.
     * @throws SubjectInvalidException if {@code subject} is invalid.
     */
    public static MessageHeadersBuilder newInstance(final MessageDirection direction, final CharSequence thingId,
            final CharSequence subject) {

        checkNotNull(direction, MessageHeaderDefinition.DIRECTION.getKey());
        argumentNotEmpty(thingId, MessageHeaderDefinition.THING_ID.getKey());
        argumentNotEmpty(subject, MessageHeaderDefinition.SUBJECT.getKey());

        final Map<String, String> initialHeaders = new HashMap<>();
        initialHeaders.put(MessageHeaderDefinition.DIRECTION.getKey(), direction.toString());
        initialHeaders.put(MessageHeaderDefinition.THING_ID.getKey(), thingId.toString());
        initialHeaders.put(MessageHeaderDefinition.SUBJECT.getKey(), subject.toString());

        return of(initialHeaders);
    }

    /**
     * Returns a new instance of {@code MessageHeadersBuilder} initialized with the the properties of the given map.
     *
     * @param headers the header map which provides the initial properties of the builder.
     * @return a builder for creating {@code MessageHeaders} object.
     * @throws NullPointerException if {@code headers} is {@code null}.
     * @throws IllegalArgumentException if {@code headers} contains a value that did not represent its appropriate Java
     * type or if {@code headers} did lack a mandatory header.
     * @throws SubjectInvalidException if {@code headers} contains an invalid value for
     * {@link MessageHeaderDefinition#SUBJECT}.
     */
    public static MessageHeadersBuilder of(final Map<String, String> headers) {
        validateMandatoryHeaders(headers);
        return new MessageHeadersBuilder(headers);
    }

    private static void validateMandatoryHeaders(final Map<String, String> headers) {
        for (final MessageHeaderDefinition mandatoryHeader : MANDATORY_HEADERS) {
            if (!headers.containsKey(mandatoryHeader.getKey())) {
                final String msgTemplate = "The headers did not contain a value for mandatory header with key <{0}>!";
                throw new IllegalArgumentException(MessageFormat.format(msgTemplate, mandatoryHeader.getKey()));
            }
        }
    }

    /**
     * Returns a new instance of {@code MessageHeadersBuilder} initialized with the the properties of the given {@code
     * jsonObject}.
     *
     * @param jsonObject the JSON object which provides the initial properties of the builder.
     * @return a builder for creating {@code MessageHeaders} object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} contains a value that did not represent its appropriate
     * Java type or if {@code jsonObject} did lack a mandatory header.
     * @throws SubjectInvalidException if {@code jsonObject} contains an invalid value for
     * {@link MessageHeaderDefinition#SUBJECT}.
     */
    public static MessageHeadersBuilder of(final JsonObject jsonObject) {
        return of(toMap(jsonObject));
    }

    /**
     * Sets the ID of the {@code Feature} from/to which the message will be sent.
     *
     * @param featureId the ID of the Feature from/to which the Message will be sent.
     * @return this builder to allow method chaining.
     * @throws IllegalArgumentException if {@code featureId} is empty.
     */
    public MessageHeadersBuilder featureId(@Nullable final CharSequence featureId) {
        putCharSequence(MessageHeaderDefinition.FEATURE_ID, featureId);
        return myself;
    }

    /**
     * Sets the MIME contentType of the payload of the Message.
     *
     * @param contentType the MIME contentType of the payload of the message.
     * @return this builder to allow method chaining.
     * @throws IllegalArgumentException if {@code contentType} is empty.
     */
    public MessageHeadersBuilder contentType(@Nullable final CharSequence contentType) {
        putCharSequence(DittoHeaderDefinition.CONTENT_TYPE, contentType);
        return myself;
    }

    /**
     * Sets the timeout of the Message to build.
     *
     * @param timeout the duration of the Message to time out.
     * @return this builder to allow method chaining.
     */
    public MessageHeadersBuilder timeout(@Nullable final Duration timeout) {
        final MessageHeaderDefinition definition = MessageHeaderDefinition.TIMEOUT;
        if (null != timeout) {
            putCharSequence(definition, String.valueOf(timeout.getSeconds()));
        } else {
            removeHeader(definition.getKey());
        }
        return myself;
    }

    /**
     * Sets the timeout of the Message to build.
     *
     * @param timeoutInSeconds the seconds of the Message to time out.
     * @return this builder to allow method chaining.
     */
    public MessageHeadersBuilder timeout(final long timeoutInSeconds) {
        return timeout(Duration.ofSeconds(timeoutInSeconds));
    }

    /**
     * Sets the timestamp of the Message to build.
     *
     * @param timestamp the timestamp of the message.
     * @return this builder to allow method chaining.
     */
    public MessageHeadersBuilder timestamp(@Nullable final OffsetDateTime timestamp) {
        final MessageHeaderDefinition definition = MessageHeaderDefinition.TIMESTAMP;
        if (null != timestamp) {
            putCharSequence(definition, timestamp.toString());
        } else {
            removeHeader(definition.getKey());
        }
        return myself;
    }

    /**
     * Sets the timestamp of the Message to build.
     *
     * @param timestampISO8601 the timestamp of the message in IS0-8601 format.
     * @return this builder to allow method chaining.
     * @throws java.time.format.DateTimeParseException if the timestamp is not in ISO-8601 format.
     */
    public MessageHeadersBuilder timestamp(@Nullable final CharSequence timestampISO8601) {
        return timestamp((null != timestampISO8601) ? OffsetDateTime.parse(timestampISO8601) : null);
    }

    /**
     * Sets the status code of the Message to build.
     *
     * @param statusCode the status code.
     * @return this builder to allow method chaining.
     */
    public MessageHeadersBuilder statusCode(@Nullable final HttpStatusCode statusCode) {
        final MessageHeaderDefinition definition = MessageHeaderDefinition.STATUS_CODE;
        if (null != statusCode) {
            putCharSequence(definition, String.valueOf(statusCode.toInt()));
        } else {
            removeHeader(definition.getKey());
        }
        return myself;
    }

    /**
     * Sets the status code of the Message to build.
     *
     * @param statusCode the status code.
     * @return this builder to allow method chaining.
     * @throws IllegalArgumentException if {@code statusCode} is unknown.
     */
    public MessageHeadersBuilder statusCode(final int statusCode) {
        return statusCode(HttpStatusCode.forInt(statusCode).orElseThrow(() -> {
            final String msg = MessageFormat.format("HTTP status code <{0}> is unknown!", statusCode);
            return new IllegalArgumentException(msg);
        }));
    }

    /**
     * Sets the specified URL which is used for message validation.
     *
     * @param validationUrl the validation URL to be set.
     * @return this builder to allow method chaining.
     * @throws IllegalArgumentException if {@code validationUrl} is empty.
     */
    public MessageHeadersBuilder validationUrl(@Nullable final CharSequence validationUrl) {
        final MessageHeaderDefinition definition = MessageHeaderDefinition.VALIDATION_URL;
        if (null != validationUrl) {
            putCharSequence(definition, validationUrl);
        } else {
            removeHeader(definition.getKey());
        }
        return myself;
    }

    @Override
    protected void validateValueType(final CharSequence key, final CharSequence value) {
        super.validateValueType(key, value);
        MessageHeaderDefinition.forKey(key).ifPresent(definition -> {
            if (MANDATORY_HEADERS.contains(definition)) {
                final String msgTemplate = "Value for mandatory header with key <{0}> cannot be overwritten!";
                throw new IllegalArgumentException(MessageFormat.format(msgTemplate, key));
            }
            definition.validateValue(value);
        });
    }

    @Override
    protected MessageHeaders doBuild(final DittoHeaders dittoHeaders) {
        return ImmutableMessageHeaders.of(dittoHeaders);
    }

}
