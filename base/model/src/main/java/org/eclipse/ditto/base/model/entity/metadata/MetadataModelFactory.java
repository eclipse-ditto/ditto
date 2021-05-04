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
package org.eclipse.ditto.base.model.entity.metadata;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointerInvalidException;

/**
 * Factory that creates new {@code metadata} objects.
 *
 * @since 1.2.0
 */
@Immutable
public final class MetadataModelFactory {

    private static final Pattern METADATA_POINTER_PATTERN = Pattern.compile("^[^/].*[^/]$|[^/]");

    /*
     * Inhibit instantiation of this utility class.
     */
    private MetadataModelFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new immutable empty {@link org.eclipse.ditto.base.model.entity.metadata.Metadata}.
     *
     * @return the new immutable empty {@code Metadata}.
     */
    public static Metadata emptyMetadata() {
        return ImmutableMetadata.empty();
    }

    /**
     * Returns a new immutable {@link org.eclipse.ditto.base.model.entity.metadata.Metadata} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code Metadata}.
     */
    public static Metadata nullMetadata() {
        return NullMetadata.newInstance();
    }


    /**
     * Returns a new immutable {@link org.eclipse.ditto.base.model.entity.metadata.Metadata} which is initialised with the values of the given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the new immutable initialised {@code Metadata}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws JsonPointerInvalidException if {@code jsonObject} contained leading or trailing slashes.
     */
    public static Metadata newMetadata(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object for initialization");

        if (!jsonObject.isNull()) {
            for (final JsonKey key : jsonObject.getKeys()) {
                final Matcher matcher =
                    METADATA_POINTER_PATTERN.matcher(key);
                if (!matcher.matches()) {
                    throw JsonPointerInvalidException.newBuilderForOuterSlashes(key).build();
                }
            }
            return ImmutableMetadata.of(jsonObject);
        } else {
            return nullMetadata();
        }
    }

    /**
     * Returns a new immutable {@link org.eclipse.ditto.base.model.entity.metadata.Metadata} which is initialised with the values of the given JSON string. This
     * string is required to be a valid {@link JsonObject}.
     *
     * @param jsonString provides the initial values of the result;
     * @return the new immutable initialised {@code Metadata}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonString} cannot be parsed to {@code
     * Attributes}.
     */
    public static Metadata newMetadata(final String jsonString) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newMetadata(jsonObject);
    }

    /**
     * Returns a new empty builder for a {@link org.eclipse.ditto.base.model.entity.metadata.Metadata}.
     *
     * @return the builder.
     */
    public static MetadataBuilder newMetadataBuilder() {
        return ImmutableMetadataBuilder.empty();
    }

    /**
     * Returns a new builder for a {@link org.eclipse.ditto.base.model.entity.metadata.Metadata} which is initialised with the values of the given Metadata.
     *
     * @param metadata provides the initial values of the result.
     * @return the builder.
     * @throws NullPointerException if {@code metadata} is {@code null}.
     */
    public static MetadataBuilder newMetadataBuilder(final Metadata metadata) {
        return ImmutableMetadataBuilder.of(metadata);
    }

}
