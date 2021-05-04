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
package org.eclipse.ditto.messages.model;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.things.model.ThingId;

/**
 * Factory that creates new {@code messages} objects.
 */
@Immutable
public final class MessagesModelFactory {

    /*
     * Inhibit instantiation of this utility class.
     */
    private MessagesModelFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a mutable builder with a fluent API for building an immutable {@link Message}.
     *
     * @param messageHeaders the headers of the message to be built.
     * @param <T> the type of the Message's payload.
     * @return the new builder.
     * @throws NullPointerException if {@code messageHeaders} is {@code null}.
     * @see #newHeadersBuilder(MessageDirection, ThingId, CharSequence)
     */
    public static <T> MessageBuilder<T> newMessageBuilder(final MessageHeaders messageHeaders) {
        return ImmutableMessageBuilder.newInstance(messageHeaders);
    }

    /**
     * Returns a new builder for {@link MessageHeaders}.
     *
     * @param direction the direction of the message.
     * @param thingId the thing ID of the message.
     * @param subject the subject of the message.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} or {@code subject} is empty.
     * @throws SubjectInvalidException if {@code subject} is invalid.
     */
    public static MessageHeadersBuilder newHeadersBuilder(final MessageDirection direction,
            final ThingId thingId, final CharSequence subject) {

        return MessageHeadersBuilder.newInstance(direction, thingId, subject);
    }

    /**
     * Returns a new builder for {@link MessageHeaders}.
     *
     * @return the builder.
     * @throws NullPointerException if {@code initialHeaders} is {@code null}.
     * @throws IllegalArgumentException if {@code initialHeaders} contains a value that did not represent its
     * appropriate Java type or if {@code initialHeaders} did lack a mandatory header.
     * @throws SubjectInvalidException if {@code initialHeaders} contains an invalid value for
     * {@link MessageHeaderDefinition#SUBJECT}.
     */
    public static MessageHeadersBuilder newHeadersBuilder(final Map<String, String> initialHeaders) {
        return MessageHeadersBuilder.of(initialHeaders);
    }

}
