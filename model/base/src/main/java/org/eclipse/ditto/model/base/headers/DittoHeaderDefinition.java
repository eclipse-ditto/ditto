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

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;

/**
 * Enumeration of definitions of well known Ditto Headers including their key and Java type.
 */
public enum DittoHeaderDefinition implements HeaderDefinition {

    /**
     * Header definition for Authorization Subjects value.
     * <p>
     * Key: {@code "auth-subjects"}, Java type: {@link JsonArray}.
     */
    AUTHORIZATION_SUBJECTS("auth-subjects", JsonArray.class),

    /**
     * Header definition for correlation Id value.
     * <p>
     * Key: {@code "correlation-id"}, Java type: String.
     */
    CORRELATION_ID("correlation-id", String.class),

    /**
     * Header definition for schema version value.
     * <p>
     * Key: {@code "version"}, Java type: {@code int}.
     */
    SCHEMA_VERSION("version", int.class),

    /**
     * Header definition for source value.
     * <p>
     * Key: {@code "source"}, Java type: String.
     */
    SOURCE("source", String.class),

    /**
     * Header definition for response required value.
     * <p>
     * Key: {@code "response-required"}, Java type: {@code boolean}.
     */
    RESPONSE_REQUIRED("response-required", boolean.class),

    /**
     * Header definition for dry run value.
     * <p>
     * Key: {@code "dry-run"}, Java type: {@code boolean}.
     */
    DRY_RUN("dry-run", boolean.class),

    /**
     * Header definition for read subjects value.
     * <p>
     * Key: {@code "read-subjects"}, Java type: {@link JsonArray}.
     */
    READ_SUBJECTS("read-subjects", JsonArray.class),

    /**
     * Header definition for channel value meaning distinguishing between live/twin.
     * <p>
     * Key: {@code "channel"}, Java type: {@link String}.
     */
    CHANNEL("channel", String.class);

    private final String key;
    private final Class<?> type;

    private DittoHeaderDefinition(final String theKey, final Class<?> theType) {
        key = theKey;
        type = theType;
    }

    /**
     * Finds an appropriate {@code DittoHeaderKey} for the specified key.
     *
     * @param key the key to look up.
     * @return the DittoHeaderKey or an empty Optional.
     */
    public static Optional<DittoHeaderDefinition> forKey(@Nullable final CharSequence key) {
        return Stream.of(values())
                .filter(dittoHeaderKey -> Objects.equals(dittoHeaderKey.key, String.valueOf(key)))
                .findAny();
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
