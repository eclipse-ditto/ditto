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
package org.eclipse.ditto.model.base.headers.metadata;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Comparator;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonValue;

/**
 * Default implementation of {@link MetadataHeader}.
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
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "key=" + key +
                ", value=" + value +
                "]";
    }

}
