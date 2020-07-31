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
package org.eclipse.ditto.services.utils.persistentactors.events;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Comparator;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonValue;

/**
 * Association between a {@link MetadataHeaderKey} and a {@link JsonValue}.
 *
 * @since 1.2.0
 */
@Immutable
final class MetadataHeader implements Comparable<MetadataHeader> {

    private final MetadataHeaderKey key;
    private final JsonValue value;

    private MetadataHeader(final MetadataHeaderKey key, final JsonValue value) {
        this.key = checkNotNull(key, "key");
        this.value = checkNotNull(value, "value");
    }

    /**
     * Returns an instance of {@code MetadataHeader}.
     *
     * @param key the key of the header.
     * @param value the value of the header.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static MetadataHeader of(final MetadataHeaderKey key, final JsonValue value) {
        return new MetadataHeader(key, value);
    }

    /**
     * Returns the key of this header.
     *
     * @return the key.
     */
    MetadataHeaderKey getKey() {
        return key;
    }

    /**
     * Returns the value of this header.
     *
     * @return the value.
     */
    JsonValue getValue() {
        return value;
    }

    @Override
    public int compareTo(final MetadataHeader metadataHeader) {
        checkNotNull(metadataHeader, "metadataHeader");

        final int result;
        if (equals(metadataHeader)) {
            result = 0;
        } else {
            final int keyComparisonResult = key.compareTo(metadataHeader.key);
            if (0 == keyComparisonResult) {

                // as both keys are equal the values determine the final result
                result = compareValues(metadataHeader.value);
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
        final MetadataHeader that = (MetadataHeader) o;
        return key.equals(that.key) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "key=" + key.asString() +
                ", value=" + value +
                "]";
    }

}
