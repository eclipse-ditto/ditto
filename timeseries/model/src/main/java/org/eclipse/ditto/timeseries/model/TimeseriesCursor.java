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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;

/**
 * An opaque, resumable position within a raw timeseries read. Pagination is
 * <em>keyset</em>-based: a cursor captures the {@code (timestamp, revision)} of the last data point
 * of a page, and the next page returns the points that sort strictly after it. Because the time
 * axis of an append-only series is immutable, a keyset cursor is stable against concurrent writes —
 * unlike an offset, which shifts whenever a point is inserted.
 * <p>
 * The pair {@code (timestamp, revision)} is a total order within a single path: one Thing event
 * carries exactly one revision and one timestamp and changes a given property at most once, so no
 * two data points of the same path share both. {@code revision} is therefore the tie-breaker that
 * makes same-millisecond points paginable without skips or duplicates.
 * <p>
 * The wire form is the Base64-URL encoding (no padding) of the compact JSON object
 * {@code {"t":"<iso-8601>","r":<revision>}}. It is deliberately opaque: callers must treat it as a
 * blob obtained from {@link TimeseriesResultMeta#getNextCursor()} and echo it back verbatim, so the
 * encoding can evolve without breaking clients.
 *
 * @since 4.0.0
 */
@Immutable
public final class TimeseriesCursor {

    /**
     * Field name for the ISO-8601 timestamp inside the decoded cursor payload.
     */
    private static final String PAYLOAD_TIMESTAMP = "t";

    /**
     * Field name for the revision inside the decoded cursor payload.
     */
    private static final String PAYLOAD_REVISION = "r";

    private final Instant timestamp;
    private final long revision;

    private TimeseriesCursor(final Instant timestamp, final long revision) {
        this.timestamp = timestamp;
        this.revision = revision;
    }

    /**
     * Returns a new {@code TimeseriesCursor} for the given keyset position.
     *
     * @param timestamp the timestamp of the last data point of a page.
     * @param revision the Thing revision of the last data point of a page (tie-breaker).
     * @return the new cursor.
     * @throws NullPointerException if {@code timestamp} is {@code null}.
     */
    public static TimeseriesCursor of(final Instant timestamp, final long revision) {
        checkNotNull(timestamp, "timestamp");
        return new TimeseriesCursor(timestamp, revision);
    }

    /**
     * Decodes an opaque cursor token previously produced by {@link #encode()}.
     *
     * @param encoded the opaque token, e.g. from {@link TimeseriesResultMeta#getNextCursor()}.
     * @return the decoded cursor.
     * @throws NullPointerException if {@code encoded} is {@code null}.
     * @throws TimeseriesQueryInvalidException if the token is not a valid cursor (HTTP 400).
     */
    public static TimeseriesCursor decode(final String encoded) {
        checkNotNull(encoded, "encoded");
        final JsonObject payload;
        try {
            // Base64 decode throws IllegalArgumentException, JSON parse throws JsonParseException —
            // both are RuntimeExceptions, caught together as a single malformed-cursor 400.
            final byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            payload = JsonFactory.newObject(new String(decoded, StandardCharsets.UTF_8));
        } catch (final RuntimeException e) {
            throw invalidCursor(encoded, e);
        }
        final String rawTimestamp = payload.getValue(PAYLOAD_TIMESTAMP)
                .filter(value -> value.isString())
                .map(value -> value.asString())
                .orElseThrow(() -> invalidCursor(encoded, null));
        final long revision = payload.getValue(PAYLOAD_REVISION)
                .filter(value -> value.isNumber())
                .map(value -> value.asLong())
                .orElseThrow(() -> invalidCursor(encoded, null));
        try {
            return new TimeseriesCursor(Instant.parse(rawTimestamp), revision);
        } catch (final DateTimeParseException e) {
            throw invalidCursor(encoded, e);
        }
    }

    private static TimeseriesQueryInvalidException invalidCursor(final String encoded,
            final Throwable cause) {
        return TimeseriesQueryInvalidException.newBuilder(
                        "The <cursor> value <" + encoded + "> is not a valid pagination cursor.")
                .description("Pass back the opaque 'nextCursor' value from a previous response " +
                        "unchanged, or omit it to start from the beginning of the range.")
                .cause(cause)
                .build();
    }

    /**
     * @return the timestamp of the keyset position.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * @return the Thing revision of the keyset position (tie-breaker for same-timestamp points).
     */
    public long getRevision() {
        return revision;
    }

    /**
     * Encodes this cursor into its opaque wire token.
     *
     * @return the Base64-URL (unpadded) token.
     */
    public String encode() {
        final JsonObject payload = JsonFactory.newObjectBuilder()
                .set(PAYLOAD_TIMESTAMP, timestamp.toString())
                .set(PAYLOAD_REVISION, revision)
                .build();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TimeseriesCursor)) {
            return false;
        }
        final TimeseriesCursor that = (TimeseriesCursor) o;
        return revision == that.revision && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, revision);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "timestamp=" + timestamp +
                ", revision=" + revision +
                "]";
    }
}
