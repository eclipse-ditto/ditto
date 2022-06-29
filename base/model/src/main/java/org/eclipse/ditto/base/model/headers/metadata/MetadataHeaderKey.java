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

import org.eclipse.ditto.json.JsonPointer;

/**
 * Key of a Ditto metadata header that is associated with a metadata value within DittoHeaders.
 * The string representation of a metadata header key consists of a path that is represented as {@link JsonPointer}.
 * Furthermore, a MetadataHeaderKey has knowledge about for which parts of a JSON value the associated header value is
 * applicable.
 * This knowledge is derived from the path.
 *
 * @since 1.2.0
 */
public interface MetadataHeaderKey extends Comparable<MetadataHeaderKey> {

    /**
     * Parses the given char sequence to obtain an instance of MetadataHeaderKey.
     *
     * @param key the key to be parsed.
     * @return the MetadataHeaderKey.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key}
     * <ul>
     *     <li>is empty,</li>
     *     <li>starts with an asterisk ({@code *}) and has not exactly two levels,</li>
     *     <li>contains an asterisk at any level but the first.</li>
     * </ul>
     */
    static MetadataHeaderKey parse(final CharSequence key) {
        return MetadataPackageFactory.parseMetadataHeaderKey(key);
    }

    /**
     * Returns an instance of MetadataHeaderKey.
     *
     * @param path the path of the key.
     * @return the instance.
     * @throws NullPointerException if {@code path} is {@code null}.
     * @throws IllegalArgumentException if {@code key}
     * <ul>
     *     <li>is empty,</li>
     *     <li>starts with an asterisk ({@code *}) and has not exactly two levels,</li>
     *     <li>contains an asterisk at any level but the first.</li>
     * </ul>
     */
    static MetadataHeaderKey of(final JsonPointer path) {
        return MetadataPackageFactory.getMetadataHeaderKey(path);
    }

    /**
     * Indicates whether the metadata value which is associated with this key should be applied to all JSON leaves
     * affected by a modifying command.
     *
     * @return {@code true} if the value of this key is applicable to all JSON leaves affected by a modifying command,
     * {@code false} else.
     */
    boolean appliesToAllLeaves();

    /**
     * Returns the path of this key.
     * The returned path is determined by the fact whether this key should be applied to all levels:
     * If this key applies to all leaves, only the leaf of the original path is returned, i.e. the wildcard at the
     * beginning is truncated.
     * Otherwise, the full original path is returned.
     *
     * @return the path.
     * @see #appliesToAllLeaves()
     */
    JsonPointer getPath();

    /**
     * Returns this key as string as it would appear in DittoHeaders.
     *
     * @return the string representation of this key.
     */
    String toString();

    /**
     * Compares the given key with this key.
     * Generally the result is obtained by comparing the key's paths.
     * Keys that have a wildcard are regarded to be less than keys with a specific path.
     *
     * @param other the key to be compared.
     * @return a negative integer, zero, or a positive integer as this key is less than, equal to, or greater than the
     * other key.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    @Override
    int compareTo(MetadataHeaderKey other);

}
