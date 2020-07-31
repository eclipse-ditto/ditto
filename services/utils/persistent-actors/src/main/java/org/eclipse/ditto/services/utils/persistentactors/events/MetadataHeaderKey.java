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

import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Key of a Ditto metadata header that is associated with a metadata value within DittoHeaders.
 * The string representation of a metadata header key consists of two parts: the prefix {@value #PREFIX} followed by a
 * path.
 * This class provides access to the path as {@link JsonPointer}.
 * Furthermore a MetadataHeaderKey has knowledge about for which parts of a JSON value the associated header value is
 * applicable.
 * This knowledge is derived from the path.
 *
 * @since 1.2.0
 */
@Immutable
final class MetadataHeaderKey implements Comparable<MetadataHeaderKey> {

    static final String PREFIX = "ditto-metadata:";

    static final JsonKey HIERARCHY_WILDCARD = JsonKey.of("*");

    private final JsonPointer path;

    private MetadataHeaderKey(final JsonPointer path) {
        this.path = path;
    }

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
    public static MetadataHeaderKey parse(final CharSequence key) {
        return of(JsonPointer.of(key.subSequence(PREFIX.length(), key.length())));
    }

    /**
     * Returns an instance of {@code MetadataHeaderKey}.
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
    public static MetadataHeaderKey of(final JsonPointer path) {
        final MetadataHeaderKey result = new MetadataHeaderKey(checkNotNull(path, "path"));
        result.validate();
        return result;
    }

    private void validate() {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("The path of a metadata key must not be empty!");
        }
        if (appliesToAllLeaves() && 2 != path.getLevelCount()) {
            final String msgPattern = "A wildcard metadata key path must have exactly two levels but it had <{0}>!";
            throw new IllegalArgumentException(MessageFormat.format(msgPattern, path.getLevelCount()));
        }
        final AtomicInteger levelCounter = new AtomicInteger(0);
        path.forEach(jsonKey -> {
            final int currentLvl = levelCounter.getAndIncrement();
            if (0 < currentLvl && jsonKey.equals(HIERARCHY_WILDCARD)) {
                final String msgPattern = "A metadata key path must not contain <{0}> at level <{1}>!";
                throw new IllegalArgumentException(MessageFormat.format(msgPattern, HIERARCHY_WILDCARD, currentLvl));
            }
        });
    }

    /**
     * Indicates whether the metadata value which is associated with this key should be applied to all JSON leaves
     * affected by a modifying command.
     *
     * @return {@code true} if the value of this key is applicable to all JSON leaves affected by a modifying command,
     * {@code false} else.
     */
    boolean appliesToAllLeaves() {
        return path.getRoot()
                .filter(HIERARCHY_WILDCARD::equals)
                .isPresent();
    }

    /**
     * Returns the path of this key.
     * The returned path is determined by the fact whether this key should be applied to all levels:
     * If this key applies to all leaves, only the leaf of the original path is returned, i. e. the wildcard at the
     * beginning is truncated.
     * Otherwise the full original path is returned.
     *
     * @return the path.
     * @see #appliesToAllLeaves()
     */
    JsonPointer getPath() {
        final JsonPointer result;
        if (appliesToAllLeaves()) {

            // The sub-pointer consists only of the leaf in this case. This is guaranteed by validation.
            result = path.getSubPointer(1).orElseThrow();
        } else {
            result = path;
        }
        return result;
    }

    /**
     * Compares the given given key with this key.
     * Generally the result is obtained by comparing the key's paths.
     * Keys that have a wildcard are regarded to be less than keys with a specific path.
     *
     * @param other the key to be compared.
     * @return a negative integer, zero, or a positive integer as this key is less than, equal to, or greater than the
     * other key.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    @Override
    public int compareTo(final MetadataHeaderKey other) {
        checkNotNull(other, "other");

        final int result;
        if (equals(other)) {
            result = 0;
        } else if (appliesToAllLeaves()) {
            if (other.appliesToAllLeaves()) {
                result = comparePaths(other.getPath());
            } else {

                // This path has a wildcard, the other path is specific.
                // This path has to be less than the other one in order to let the value of a specific path overwrite
                // the value of a wildcard path.
                result = -1;
            }
        } else {
            if (!other.appliesToAllLeaves()) {
                result = comparePaths(other.getPath());
            } else {

                // This path is specific, the other path has a wildcard.
                // This path has to be greater than the other one in order to let the value of a specific path overwrite
                // the value of a wildcard path.
                result = 1;
            }
        }
        return result;
    }

    private int comparePaths(final JsonPointer otherPath) {
        final Comparator<JsonPointer> jsonPointerComparator = Comparator.comparing(JsonPointer::toString);

        // String comparison returns the difference of the length of the strings.
        // This operation normalizes the string comparison result to either -1, 0 or 1 to facilitate testing.
        return Integer.compare(jsonPointerComparator.compare(path, otherPath), 0);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MetadataHeaderKey that = (MetadataHeaderKey) o;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    /**
     * Returns this key as string as it would appear in DittoHeaders.
     *
     * @return the string representation of this key.
     */
    public String asString() {
        return PREFIX + path;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "path=" + path +
                "]";
    }

}
