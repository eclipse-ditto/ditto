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
package org.eclipse.ditto.model.base.headers;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;

/**
 * Enumeration of definitions of well known Ditto Headers including their key and Java type.
 */
public enum DittoHeaderDefinition implements HeaderDefinition {

    /**
     * Header definition for Authorization Subjects value.
     * <p>
     * Key: {@code "auth-subjects"}, Java type: {@link JsonArray}.
     * </p>
     */
    AUTHORIZATION_SUBJECTS("auth-subjects", JsonArray.class, false, true),

    /**
     * Header definition for correlation Id value.
     * <p>
     * Key: {@code "correlation-id"}, Java type: String.
     * </p>
     */
    CORRELATION_ID("correlation-id", String.class, true, true),

    /**
     * Header definition for schema version value.
     * <p>
     * Key: {@code "version"}, Java type: {@code int}.
     * </p>
     */
    SCHEMA_VERSION("version", int.class, true, true),

    /**
     * Header definition for source value.
     * <p>
     * Key: {@code "source"}, Java type: String.
     * </p>
     */
    SOURCE("source", String.class, true, true),

    /**
     * Header definition for response required value.
     * <p>
     * Key: {@code "response-required"}, Java type: {@code boolean}.
     * </p>
     */
    RESPONSE_REQUIRED("response-required", boolean.class, true, true),

    /**
     * Header definition for dry run value.
     * <p>
     * Key: {@code "dry-run"}, Java type: {@code boolean}.
     * </p>
     */
    DRY_RUN("dry-run", boolean.class, false, false),

    /**
     * Header definition for read subjects value.
     * <p>
     * Key: {@code "read-subjects"}, Java type: {@link JsonArray}.
     * </p>
     */
    READ_SUBJECTS("read-subjects", JsonArray.class, false, false),

    /**
     * Header definition for a signal's content-type.
     * <p>
     * Key: {@code "content-type"}, Java type: String.
     * </p>
     */
    CONTENT_TYPE("content-type", String.class, true, true),

    /**
     * Header definition for channel value meaning distinguishing between live/twin.
     * <p>
     * Key: {@code "channel"}, Java type: {@link String}.
     * </p>
     */
    CHANNEL("channel", String.class, false, false),

    /**
     * Header definition for origin value that is set to the id of the originating session.
     * <p>
     * Key: {@code "origin"}, Java type: {@link String}.
     * </p>
     */
    ORIGIN("origin", String.class, false, false),

    /**
     * Header definition for "ETag".
     * <p>
     * Key: {@code "ETag"}, Java type: {@link String}.
     * </p>
     */
    ETAG("ETag", EntityTag.class, String.class, false, true),

    /**
     * Header definition for "If-Match".
     * <p>
     * Key: {@code "If-Match"}, Java type: {@link String}.
     * </p>
     */
    IF_MATCH("If-Match", EntityTagMatchers.class, String.class, true, false),

    /**
     * Header definition for "If-None-Match".
     * <p>
     * Key: {@code "If-None-Match"}, Java type: {@link String}.
     * </p>
     */
    IF_NONE_MATCH("If-None-Match", EntityTagMatchers.class, String.class, true, false);

    /**
     * Map to speed up lookup of header definition by key.
     */
    private static final Map<CharSequence, DittoHeaderDefinition> VALUES_BY_KEY = Arrays.stream(values())
            .collect(Collectors.toMap(DittoHeaderDefinition::getKey, Function.identity()));

    private final String key;
    private final Class<?> type;
    private final Class<?> serializationType;
    private final boolean readFromExternalHeaders;
    private final boolean writeToExternalHeaders;

    /**
     * @param theKey the key used as key for header map.
     * @param theType the Java type of the header value which is associated with this definition's key.
     * @param readFromExternalHeaders whether Ditto reads this header from headers sent by externals.
     * @param writeToExternalHeaders whether Ditto publishes this header to externals.
     */
    DittoHeaderDefinition(final String theKey, final Class<?> theType, final boolean readFromExternalHeaders,
            final boolean writeToExternalHeaders) {
        this(theKey, theType, theType, readFromExternalHeaders, writeToExternalHeaders);
    }

    /**
     * @param theKey the key used as key for header map.
     * @param theType the Java type of the header value which is associated with this definition's key.
     * @param serializationType the type to which this header value should be serialized.
     * @param readFromExternalHeaders whether Ditto reads this header from headers sent by externals.
     * @param writeToExternalHeaders whether Ditto publishes this header to externals.
     */
    DittoHeaderDefinition(final String theKey, final Class<?> theType, final Class<?> serializationType,
            final boolean readFromExternalHeaders, final boolean writeToExternalHeaders) {
        key = theKey.toLowerCase();
        type = theType;
        this.serializationType = serializationType;
        this.readFromExternalHeaders = readFromExternalHeaders;
        this.writeToExternalHeaders = writeToExternalHeaders;
    }

    /**
     * Finds an appropriate {@code DittoHeaderKey} for the specified key.
     *
     * @param key the key to look up.
     * @return the DittoHeaderKey or an empty Optional.
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

    @Override
    public Class getSerializationType() {
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
    public String toString() {
        return getKey();
    }

}
