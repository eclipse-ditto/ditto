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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Default implementation of {@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeader}.
 *
 * @since 1.2.0
 */
@Immutable
final class DefaultMetadataHeader implements MetadataHeader {

    private final MetadataHeaderKey key;
    private final JsonValue value;

    private DefaultMetadataHeader(final MetadataHeaderKey key, final JsonValue value) {
        this.key = checkNotNull(key, "key");
        this.value = checkNotNull(value, "value");
    }

    /**
     * Returns an instance of {@code DefaultMetadataHeader}.
     *
     * @param key the key of the header.
     * @param value the value of the header.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static DefaultMetadataHeader of(final MetadataHeaderKey key, final JsonValue value) {
        return new DefaultMetadataHeader(key, value);
    }

    /**
     * Derives a DefaultMetadataHeader instance from the given JSON object.
     *
     * @param jsonObject the JSON object which provides the properties of a DefaultMetadataHeader.
     * @return the instance.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} lacks either
     * <ul>
     *     <li>{@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeader.JsonFields#METADATA_KEY} or</li>
     *     <li>{@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeader.JsonFields#METADATA_VALUE}.</li>
     * </ul>
     * @throws JsonParseException if {@code jsonObject} contained an invalid value for {@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeader.JsonFields#METADATA_KEY}.
     */
    static DefaultMetadataHeader fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");
        return of(tryToGetMetadataHeaderKey(tryToGetValue(jsonObject, JsonFields.METADATA_KEY)),
                tryToGetMetadataHeaderValue(jsonObject));
    }

    private static <T> T tryToGetValue(final JsonObject metadataEntry, final JsonFieldDefinition<T> fieldDefinition) {
        return metadataEntry.getValue(fieldDefinition)
                .orElseThrow(() -> {
                    final String msgPattern = "Metadata header entry JSON object did not include required <{0}> field!";
                    return JsonMissingFieldException.newBuilder()
                            .message(MessageFormat.format(msgPattern, fieldDefinition.getPointer()))
                            .build();
                });
    }

    private static MetadataHeaderKey tryToGetMetadataHeaderKey(final String metadataHeaderKeyPath) {
        try {
            return getMetadataHeaderKey(metadataHeaderKeyPath);
        } catch (final IllegalArgumentException e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("The metadata header key <{0}> is invalid!", metadataHeaderKeyPath))
                    .description(e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private static MetadataHeaderKey getMetadataHeaderKey(final CharSequence metadataHeaderKeyPath) {
        return MetadataHeaderKey.of(JsonPointer.of(metadataHeaderKeyPath));
    }

    private static JsonValue tryToGetMetadataHeaderValue(final JsonObject metadataEntry) {
        return tryToGetValue(metadataEntry, JsonFields.METADATA_VALUE);
    }

    @Override
    public MetadataHeaderKey getKey() {
        return key;
    }

    @Override
    public JsonValue getValue() {
        return value;
    }

    @Override
    public int compareTo(final MetadataHeader metadataHeader) {
        checkNotNull(metadataHeader, "metadataHeader");

        final int result;
        if (equals(metadataHeader)) {
            result = 0;
        } else {
            final int keyComparisonResult = key.compareTo(metadataHeader.getKey());
            if (0 == keyComparisonResult) {

                // as both keys are equal the values determine the final result
                result = compareValues(metadataHeader.getValue());
            } else {
                result = keyComparisonResult;
            }
        }
        return result;
    }

    private int compareValues(final JsonValue otherValue) {
        final Comparator<JsonValue> jsonValueComparator =
                Comparator.comparing(v -> v.isString() ? v.asString() : v.toString());

        // String comparison returns the difference of the length of the strings.
        // This operation normalizes the string comparison result to either -1, 0 or 1 to facilitate testing.
        return Integer.compare(jsonValueComparator.compare(value, otherValue), 0);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMetadataHeader that = (DefaultMetadataHeader) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public JsonObject toJson() {
        return JsonObject.newBuilder()
                .set(JsonFields.METADATA_KEY, key.toString())
                .set(JsonFields.METADATA_VALUE, value)
                .build();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "key=" + key +
                ", value=" + value +
                "]";
    }

}
