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
package org.eclipse.ditto.base.model.headers.metadata;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Association between a {@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey} and a {@link JsonValue}.
 *
 * @since 1.2.0
 */
@Immutable
public interface MetadataHeader extends Comparable<MetadataHeader>, Jsonifiable<JsonObject> {

    /**
     * Returns an instance of {@code MetadataHeader}.
     *
     * @param key the key of the header.
     * @param value the value of the header.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static MetadataHeader of(final MetadataHeaderKey key, final JsonValue value) {
        return MetadataPackageFactory.getMetadataHeader(key, value);
    }

    /**
     * Derives a {@code MetadataHeader} instance from the given JSON object.
     *
     * @param jsonObject the JSON object which provides the properties of a MetadataHeader.
     * @return the instance.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} lacks either
     * <ul>
     *     <li>{@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeader.JsonFields#METADATA_KEY} or</li>
     *     <li>{@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeader.JsonFields#METADATA_VALUE}.</li>
     * </ul>
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an invalid value for
     * {@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeader.JsonFields#METADATA_KEY}.
     */
    static MetadataHeader fromJson(final JsonObject jsonObject) {
        return MetadataPackageFactory.metadataHeaderFromJson(jsonObject);
    }

    /**
     * Returns the key of this header.
     *
     * @return the key.
     */
    MetadataHeaderKey getKey();

    /**
     * Returns the value of this header.
     *
     * @return the value.
     */
    JsonValue getValue();

    /**
     * Compares this header with the given header.
     * First the keys are compared then the string representations of the header's values.
     *
     * @param metadataHeader the header to be compared.
     * @return a negative integer, zero, or a positive integer as this header is less than, equal to, or greater than
     * the other header.
     * @throws NullPointerException if {@code metadataHeader} is {@code null}.
     * @see org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey#compareTo(org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey)
     */
    @Override
    int compareTo(MetadataHeader metadataHeader);

    /**
     * This class provides definitions of fields of a {@code MetadataHeader} JSON object representation.
     */
    final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        /**
         * Defines a field of a JSON object whose value is a metadata key.
         */
        public static final JsonFieldDefinition<String> METADATA_KEY =
                JsonFieldDefinition.ofString("key", FieldType.REGULAR);

        /**
         * Defines a field of a JSON object whose value is a metadata value.
         */
        public static final JsonFieldDefinition<JsonValue> METADATA_VALUE =
                JsonFieldDefinition.ofJsonValue("value", FieldType.REGULAR);

    }

}
