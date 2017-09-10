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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * This interface represents headers to be used for {@link Message}s.
 * <p>
 * <em>Implementations of this interface are required to be immutable.</em>
 */
@Immutable
public interface MessageHeaders extends DittoHeaders {

    /**
     * Returns a new builder with a fluent API for an immutable MessageHeaders object.
     *
     * @param direction the direction of the message.
     * @param thingId the thing ID of the message.
     * @param subject the subject of the message.
     * @return the builder;
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} or {@code subject} is empty.
     * @throws SubjectInvalidException if {@code subject} is invalid.
     */
    static MessageHeadersBuilder newBuilder(final MessageDirection direction,
            final CharSequence thingId, final CharSequence subject) {

        return MessagesModelFactory.newHeadersBuilder(direction, thingId, subject);
    }

    /**
     * Returns a new builder with a fluent API for an immutable MessageHeaders object for a Claim Message.
     *
     * @param thingId the thing ID of the message.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} is empty.
     */
    static MessageHeadersBuilder newBuilderForClaiming(final CharSequence thingId) {
        return newBuilder(MessageDirection.TO, thingId, KnownMessageSubjects.CLAIM_SUBJECT);
    }

    /**
     * Returns a new instance of {@code MessageDirection} which is based on the specified map.
     *
     * @param headers the header key-value-pairs.
     * @return the instance.
     * @throws NullPointerException if {@code headers} is {@code null}.
     * @throws IllegalArgumentException if {@code headers} contains a value that did not represent its appropriate Java
     * type or if {@code headers} did lack a mandatory header.
     * @throws SubjectInvalidException if {@code headers} contains an invalid value for
     * {@link MessageHeaderDefinition#SUBJECT}.
     */
    static MessageHeaders of(final Map<String, String> headers) {
        return MessageHeadersBuilder.of(headers).build();
    }

    /**
     * Returns a new instance of {@code MessageHeaders} which is based on the specified JSON object.
     *
     * @param jsonObject the JSON object representation of message headers.
     * @return the instance.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} contains a value that did not represent its appropriate
     * Java type or if {@code jsonObject} did lack a mandatory header.
     * @throws SubjectInvalidException if {@code jsonObject} contains an invalid value for
     * {@link MessageHeaderDefinition#SUBJECT}.
     */
    static MessageHeaders of(final JsonObject jsonObject) {
        return MessageHeadersBuilder.of(jsonObject).build();
    }

    @Override
    default MessageHeadersBuilder toBuilder() {
        return MessageHeadersBuilder.of(this);
    }

    /**
     * Returns the direction of the message, specifying if the message has been sent <em>FROM</em> a {@code Thing} (or
     * its {@code Feature}), or <em>TO</em> a {@code Thing} (or its {@code Feature}).
     *
     * @return the direction.
     * @throws IllegalStateException if this headers did not contain the direction.
     */
    MessageDirection getDirection();

    /**
     * Returns the subject of the message.
     *
     * @return the subject.
     * @throws IllegalStateException if this headers did not contain the subject.
     */
    String getSubject();

    /**
     * Returns the ID of the {@code Thing} from/to which this message is sent.
     *
     * @return the thing ID.
     * @throws IllegalStateException if this headers did not contain the thing ID.
     */
    String getThingId();

    /**
     * Returns the ID of the {@code Feature} from/to which this message is sent (may be empty if the message is not sent
     * from or addressed to a specific feature).
     *
     * @return the feature ID.
     */
    Optional<String> getFeatureId();

    /**
     * Returns the content-type of the payload as provided by the message sender (may be empty if the message has no
     * payload).
     *
     * @return the content type.
     */
    Optional<String> getContentType();

    /**
     * Returns the timeout of the message.
     *
     * @return the timeout.
     */
    Optional<Duration> getTimeout();

    /**
     * Returns the timestamp of the message.
     *
     * @return the timestamp.
     */
    Optional<OffsetDateTime> getTimestamp();

    /**
     * Returns the status code of the message.
     *
     * @return the status code.
     */
    Optional<HttpStatusCode> getStatusCode();

    /**
     * Returns the URL to be used for message validation.
     *
     * @return the validation URL.
     */
    Optional<String> getValidationUrl();

}
