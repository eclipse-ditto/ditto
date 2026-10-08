/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * One grouping dimension of a cross-Thing timeseries aggregation.
 * <p>
 * Three kinds are supported, all of which resolve against fields the backend already keeps in its
 * indexed metadata, so grouping never requires reading the measurement values:
 * <ul>
 *   <li>{@link Kind#THING_ID} — one series per Thing ({@code groupBy=thingId});</li>
 *   <li>{@link Kind#PATH} — one series per requested path ({@code groupBy=path}). Note that results
 *       are <em>always</em> reported per path, so this dimension is implicit; declaring it
 *       explicitly only affects the emitted group identity;</li>
 *   <li>{@link Kind#TAG} — one series per distinct value of an ingest-time tag
 *       ({@code groupBy=tag:building}).</li>
 * </ul>
 * The wire form is the same string used in the {@code groupBy} query parameter, so
 * {@link #parse(String)} and {@link #toString()} round-trip.
 * <p>
 * Grouping by a tag groups on the value that was <em>frozen at ingest</em>, not on the Thing's
 * current attribute — see {@link CrossThingTimeseriesQuery} for why that differs from selecting
 * Things by their present state.
 *
 * @since 4.0.0
 */
@Immutable
public final class GroupBy {

    /** Wire prefix marking a tag dimension, e.g. {@code tag:building}. */
    private static final String TAG_PREFIX = "tag:";

    private static final String THING_ID_TOKEN = "thingId";
    private static final String PATH_TOKEN = "path";

    private static final GroupBy THING_ID_INSTANCE = new GroupBy(Kind.THING_ID, null);
    private static final GroupBy PATH_INSTANCE = new GroupBy(Kind.PATH, null);

    private final Kind kind;
    @Nullable private final String tagKey;

    private GroupBy(final Kind kind, @Nullable final String tagKey) {
        this.kind = kind;
        this.tagKey = tagKey;
    }

    /**
     * @return the dimension grouping by Thing ID.
     */
    public static GroupBy thingId() {
        return THING_ID_INSTANCE;
    }

    /**
     * @return the dimension grouping by requested path.
     */
    public static GroupBy path() {
        return PATH_INSTANCE;
    }

    /**
     * Returns the dimension grouping by the ingest-time tag with the given key.
     *
     * @param tagKey the tag key to group on.
     * @return the tag dimension.
     * @throws NullPointerException if {@code tagKey} is {@code null}.
     * @throws TimeseriesQueryInvalidException if {@code tagKey} is empty.
     */
    public static GroupBy tag(final String tagKey) {
        checkNotNull(tagKey, "tagKey");
        final String trimmed = tagKey.trim();
        if (trimmed.isEmpty()) {
            throw TimeseriesQueryInvalidException.newBuilder("A 'tag:' groupBy dimension requires a " +
                    "non-empty tag key, e.g. 'tag:building'.").build();
        }
        return new GroupBy(Kind.TAG, trimmed);
    }

    /**
     * Parses a {@code groupBy} dimension from its wire form.
     *
     * @param value {@code thingId}, {@code path}, or a tag path such as {@code attributes/floor}
     * (the tag key as declared in the WoT model). {@code tag:<key>} is also accepted.
     * @return the parsed dimension.
     * @throws NullPointerException if {@code value} is {@code null}.
     * @throws TimeseriesQueryInvalidException if {@code value} is not a known dimension.
     */
    public static GroupBy parse(final String value) {
        checkNotNull(value, "value");
        final String trimmed = value.trim();
        if (THING_ID_TOKEN.equals(trimmed)) {
            return thingId();
        }
        if (PATH_TOKEN.equals(trimmed)) {
            return path();
        }
        // Anything else is a tag dimension named by its full Thing path, e.g. "attributes/floor".
        // The legacy "tag:<key>" form is still accepted so a caller mid-migration is not broken.
        if (trimmed.startsWith(TAG_PREFIX)) {
            return tag(trimmed.substring(TAG_PREFIX.length()));
        }
        if (trimmed.isEmpty()) {
            throw TimeseriesQueryInvalidException.newBuilder("A groupBy dimension must not be empty. " +
                            "Supported: 'thingId', 'path', or a tag path such as 'attributes/floor'.")
                    .build();
        }
        return tag(trimmed);
    }

    /**
     * @return the kind of this dimension.
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @return the tag key, present only for {@link Kind#TAG}.
     */
    public Optional<String> getTagKey() {
        return Optional.ofNullable(tagKey);
    }

    /**
     * Returns the key under which this dimension's value appears in an
     * {@link AggregatedTimeseriesResult#getGroup() group identity}. For a tag dimension this is the
     * bare tag key, so a group reads as {@code {"building": "A"}} rather than
     * {@code {"tag:building": "A"}}.
     *
     * @return the group-identity key.
     */
    public String getGroupKey() {
        if (kind == Kind.TAG) {
            return tagKey;
        }
        return kind == Kind.THING_ID ? THING_ID_TOKEN : PATH_TOKEN;
    }

    @Override
    public String toString() {
        if (kind == Kind.TAG) {
            return TAG_PREFIX + tagKey;
        }
        return kind == Kind.THING_ID ? THING_ID_TOKEN : PATH_TOKEN;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupBy that = (GroupBy) o;
        return kind == that.kind && Objects.equals(tagKey, that.tagKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, tagKey);
    }

    /**
     * The kind of grouping dimension.
     */
    public enum Kind {

        /** Group by Thing ID. */
        THING_ID,

        /** Group by requested path. */
        PATH,

        /** Group by the value of an ingest-time tag. */
        TAG
    }
}
