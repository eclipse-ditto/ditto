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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Default implementation of {@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey}.
 *
 * @since 1.2.0
 */
@Immutable
final class DefaultMetadataHeaderKey implements MetadataHeaderKey {

    static final JsonKey HIERARCHY_WILDCARD = JsonKey.of("*");

    private final JsonPointer path;

    private DefaultMetadataHeaderKey(final JsonPointer path) {
        this.path = path;
    }

    /**
     * Parses the given char sequence to obtain an instance of DefaultMetadataHeaderKey.
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
    public static DefaultMetadataHeaderKey parse(final CharSequence key) {
        return of(JsonPointer.of(argumentNotEmpty(key, "key")));
    }

    /**
     * Returns an instance of DefaultMetadataHeaderKey.
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
    public static DefaultMetadataHeaderKey of(final JsonPointer path) {
        final DefaultMetadataHeaderKey result = new DefaultMetadataHeaderKey(checkNotNull(path, "path"));
        result.validate();
        return result;
    }

    private void validate() {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("The path of a metadata header key must not be empty!");
        }
        if (appliesToAllLeaves() && 2 != path.getLevelCount()) {
            throw new IllegalArgumentException(MessageFormat.format(
                    "A wildcard path of a metadata header key must have exactly two levels but it had <{0}>!",
                    path.getLevelCount()));
        }
        final AtomicInteger levelCounter = new AtomicInteger(0);
        path.forEach(jsonKey -> {
            final int currentLvl = levelCounter.getAndIncrement();
            if (0 < currentLvl && jsonKey.equals(HIERARCHY_WILDCARD)) {
                throw new IllegalArgumentException(MessageFormat.format(
                        "The path of a metadata header key must not contain <{0}> at level <{1}>!",
                        HIERARCHY_WILDCARD, currentLvl));
            }
        });
    }

    @Override
    public boolean appliesToAllLeaves() {
        return path.getRoot()
                .filter(HIERARCHY_WILDCARD::equals)
                .isPresent();
    }

    @Override
    public JsonPointer getPath() {
        final JsonPointer result;
        if (appliesToAllLeaves()) {

            // The sub-pointer consists only of the leaf in this case. This is guaranteed by validation.
            result = path.getSubPointer(1).orElseThrow(NoSuchElementException::new);
        } else {
            result = path;
        }
        return result;
    }

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
        final DefaultMetadataHeaderKey that = (DefaultMetadataHeaderKey) o;
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        return path.toString();
    }

}
