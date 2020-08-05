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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonValue;

/**
 * Association between a {@link MetadataHeaderKey} and a {@link JsonValue}.
 *
 * @since 1.2.0
 */
@Immutable
public interface MetadataHeader extends Comparable<MetadataHeader> {

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
     * @see MetadataHeaderKey#compareTo(MetadataHeaderKey)
     */
    @Override
    int compareTo(MetadataHeader metadataHeader);

}
