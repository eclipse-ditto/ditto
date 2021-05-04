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

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.base.model.headers.HeaderValueValidators;
import org.eclipse.ditto.base.model.headers.ValueValidator;

/**
 * Enumeration of definitions of well known message headers including their key and Java type.
 * Note: All header keys must be lower-case;
 */
public enum MessageHeaderDefinition implements HeaderDefinition {

    /**
     * Header definition for the direction of a message.
     * <p>
     * Key: {@code "ditto-message-direction"}, Java type: String.
     * </p>
     */
    DIRECTION("ditto-message-direction", String.class, false, true, DittoMessageDirectionValueValidator.getInstance()),

    /**
     * Header definitions for the subject of a message.
     * <p>
     * Key: {@code "ditto-message-subject"}, Java type: String.
     * </p>
     */
    SUBJECT("ditto-message-subject", String.class, false, true, DittoMessageSubjectValueValidator.getInstance()),

    /**
     * Header definition for the Thing ID of a message.
     * <p>
     * Key: {@code "ditto-message-thing-id"}, Java type: String.
     * </p>
     */
    THING_ID("ditto-message-thing-id", String.class, false, true, DittoMessageThingIdValueValidator.getInstance()),

    /**
     * Header definition for the Feature ID of a message, if sent to a Feature.
     * <p>
     * Key: {@code "ditto-message-feature-id"}, Java type: String.
     * </p>
     */
    FEATURE_ID("ditto-message-feature-id", String.class, false, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header containing the timestamp of the message as ISO 8601 string.
     * <p>
     * Key: {@code "timestamp"}, Java type: String.
     * </p>
     */
    TIMESTAMP("timestamp", String.class, true, true, TimestampValueValidator.getInstance()),

    /**
     * Header definition for the status code of a message, e. g. if a message is a response to another message.
     * <p>
     * Key: {@code "status"}, Java type: {@code int}.
     * </p>
     */
    STATUS_CODE("status", int.class, true, true, HttpStatusCodeValueValidator.getInstance());

    /**
     * Map to speed up lookup of header definition by key.
     */
    private static final Map<CharSequence, MessageHeaderDefinition> VALUES_BY_KEY = Arrays.stream(values())
            .collect(Collectors.toMap(MessageHeaderDefinition::getKey, Function.identity()));

    private final String key;
    private final Class<?> type;
    private final Class<?> serializationType;
    private final boolean readFromExternalHeaders;
    private final boolean writeToExternalHeaders;
    private final ValueValidator valueValidator;

    /**
     * @param theKey the key used as key for header map.
     * @param theType the Java type of the header value which is associated with this definition's key.
     * @param readFromExternalHeaders whether Ditto reads this header from headers sent by externals.
     * @param writeToExternalHeaders whether Ditto publishes this header to externals.
     */
    MessageHeaderDefinition(final String theKey,
            final Class<?> theType,
            final boolean readFromExternalHeaders,
            final boolean writeToExternalHeaders,
            final ValueValidator valueValidator) {

        this(theKey, theType, theType, readFromExternalHeaders, writeToExternalHeaders, valueValidator);
    }

    /**
     * @param theKey the key used as key for header map.
     * @param theType the Java type of the header value which is associated with this definition's key.
     * @param serializationType the type to which this header value should be serialized.
     * @param readFromExternalHeaders whether Ditto reads this header from headers sent by externals.
     * @param writeToExternalHeaders whether Ditto publishes this header to externals.
     */
    MessageHeaderDefinition(final String theKey,
            final Class<?> theType,
            final Class<?> serializationType,
            final boolean readFromExternalHeaders,
            final boolean writeToExternalHeaders,
            final ValueValidator valueValidator) {

        key = theKey;
        type = theType;
        this.serializationType = serializationType;
        this.readFromExternalHeaders = readFromExternalHeaders;
        this.writeToExternalHeaders = writeToExternalHeaders;
        this.valueValidator = valueValidator;
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
    public Class<?> getJavaType() {
        return type;
    }

    @Override
    public Class<?> getSerializationType() {
        return serializationType;
    }

    @Override
    public boolean shouldReadFromExternalHeaders() {
        return readFromExternalHeaders;
    }

    @Override
    public boolean shouldWriteToExternalHeaders() {
        return writeToExternalHeaders;
    }

    @Override
    public void validateValue(@Nullable final CharSequence value) {
        valueValidator.accept(this, value);
    }

    @Nonnull
    @Override
    public String toString() {
        return getKey();
    }

}
