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
package org.eclipse.ditto.internal.utils.persistence.mongo.indices;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.bson.BsonInt32;

/**
 * Defines a MongoDB default index key, which consists of field name and {@link IndexDirection}.
 */
@Immutable
public final class DefaultIndexKey implements IndexKey {
    private final String fieldName;
    private final IndexDirection direction;

    private DefaultIndexKey(final String fieldName,
            final IndexDirection direction) {
        this.fieldName = requireNonNull(fieldName);
        this.direction = requireNonNull(direction);
    }

    /**
     * Creates a new {@link DefaultIndexKey} with the given {@code fieldName} and {@link IndexDirection#DEFAULT}.
     *
     * @param fieldName the field name.
     * @return the new {@link DefaultIndexKey}.
     */
    public static DefaultIndexKey of(final String fieldName) {
        return new DefaultIndexKey(fieldName, IndexDirection.DEFAULT);
    }

    /**
     * Creates a new {@link DefaultIndexKey} with the given {@code fieldName} and {@code direction}.
     *
     * @param fieldName the field name.
     * @param direction the {@link IndexDirection} for this key.
     * @return the new {@link DefaultIndexKey}.
     */
    public static DefaultIndexKey of(final String fieldName, final IndexDirection direction) {
        return new DefaultIndexKey(fieldName, direction);
    }

    @Override
    public String getFieldName() {
        return fieldName;
    }

    @Override
    public BsonInt32 getBsonValue() {
        return direction.getBsonInt();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultIndexKey that = (DefaultIndexKey) o;
        return Objects.equals(fieldName, that.fieldName) &&
                direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, direction);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "fieldName='" + fieldName + '\'' +
                ", direction=" + direction +
                ']';
    }
}
