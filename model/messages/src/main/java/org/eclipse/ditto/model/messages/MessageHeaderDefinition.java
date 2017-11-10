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

import java.text.MessageFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.common.IdValidator;
import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;

/**
 * Enumeration of definitions of well known message headers including their key and Java type.
 */
public enum MessageHeaderDefinition implements HeaderDefinition {

    /**
     * Header definition for the direction of a message.
     * <p>
     * Key: {@code "direction"}, Java type: String.
     */
    DIRECTION("direction", String.class),

    /**
     * Header definitions for the subject of a message.
     * <p>
     * Key: {@code "subject"}, Java type: String.
     */
    SUBJECT("subject", String.class) {
        @Override
        public void validateValue(@Nullable final CharSequence value) {
            super.validateValue(value);
            final Validator subjectValidator = IdValidator.newInstance(value, SUBJECT_REGEX);
            if (!subjectValidator.isValid()) {
                final String msgTemplate = "The subject <{0}> is invalid because it did not match the pattern <{1}>!";
                throw SubjectInvalidException.newBuilder(String.valueOf(value))
                        .message(() -> MessageFormat.format(msgTemplate, value, SUBJECT_REGEX))
                        .build();
            }
        }
    },

    /**
     * Header definition for the Thing ID of a message.
     * <p>
     * Key: {@code "thing-id"}, Java type: String.
     */
    THING_ID("thing-id", String.class),

    /**
     * Header definition for the Feature ID of a message, if sent to a Feature.
     * <p>
     * Key: {@code "feature-id"}, Java type: String.
     */
    FEATURE_ID("feature-id", String.class),

    /**
     * Header definition for the timeout in seconds of a message.
     * <p>
     * Key: {@code "timeout"}, Java type: {@code long}.
     */
    TIMEOUT("timeout", long.class) {
        @SuppressWarnings({"squid:S2201", "ResultOfMethodCallIgnored"})
        @Override
        public void validateValue(@Nullable final CharSequence value) {
            super.validateValue(value);
            try {
                Duration.ofSeconds(Long.parseLong(String.valueOf(value)));
            } catch (final NumberFormatException | DateTimeParseException e) {
                final String msgTemplate = "<{0}> is not a valid timeout!";
                throw new IllegalArgumentException(MessageFormat.format(msgTemplate, value), e);
            }
        }
    },

    /**
     * Header containing the timestamp of the message as ISO 8601 string.
     * <p>
     * Key: {@code "timestamp"}, Java type: String.
     */
    TIMESTAMP("timestamp", String.class) {
        @SuppressWarnings({"squid:S2201", "ResultOfMethodCallIgnored"})
        @Override
        public void validateValue(@Nullable final CharSequence value) {
            super.validateValue(value);
            try {
                OffsetDateTime.parse(String.valueOf(value));
            } catch (final DateTimeParseException e) {
                final String msgTemplate = "<{0}> is not a valid timestamp!";
                throw new IllegalArgumentException(MessageFormat.format(msgTemplate, value), e);
            }
        }
    },

    /**
     * Header definition for the status code of a message, e. g. if a message is a response to another message.
     * <p>
     * Key: {@code "status"}, Java type: {@code int}.
     */
    STATUS_CODE("status", int.class) {
        @SuppressWarnings({"squid:S2201", "ResultOfMethodCallIgnored"})
        @Override
        public void validateValue(@Nullable final CharSequence value) {
            super.validateValue(value);
            HttpStatusCode.forInt(Integer.parseInt(String.valueOf(value))).orElseThrow(() -> {
                final String msgTemplate = "<{0}> is not a HTTP status code!";
                return new IllegalArgumentException(MessageFormat.format(msgTemplate, value));
            });
        }
    },

    /**
     * Header definition for the validation URL of the message.
     * <p>
     * Key: {@code "validation-url"}, Java type: String.
     */
    VALIDATION_URL("validation-url", String.class);

    /**
     * The regex pattern a Subject has to conform to. Defined by
     * <a href="http://www.ietf.org/rfc/rfc2396.txt">RFC-2396</a> .
     */
    static final String SUBJECT_REGEX =
            "(([a-zA-Z][0-9a-zA-Z+\\-\\.]*:)?/{0,2}[0-9a-zA-Z;/?:@&=+$\\.\\-_!~*'()%]+)?(#[0-9a-zA-Z;/?:@&=+$\\.\\-_!~*'()%]+)?";

    /**
     * Map to speed up lookup of header definition by key.
     */
    private static final Map<CharSequence, MessageHeaderDefinition> VALUES_BY_KEY = Arrays.stream(values())
            .collect(Collectors.toMap(MessageHeaderDefinition::getKey, Function.identity()));

    private final String key;
    private final Class<?> type;

    MessageHeaderDefinition(final String theKey, final Class<?> theType) {
        key = theKey;
        type = theType;
    }

    /**
     * Finds an appropriate {@code MessageHeaderDefinition} for the specified key.
     *
     * @param key the key to look up.
     * @return the MessageHeaderDefinition or an empty Optional.
     */
    public static Optional<HeaderDefinition> forKey(@Nullable final CharSequence key) {
        return Optional.ofNullable(VALUES_BY_KEY.get(key));
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Class getJavaType() {
        return type;
    }

    @Nonnull
    @Override
    public String toString() {
        return getKey();
    }

}
