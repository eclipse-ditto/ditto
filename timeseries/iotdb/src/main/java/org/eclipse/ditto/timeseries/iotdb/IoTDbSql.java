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
package org.eclipse.ditto.timeseries.iotdb;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Naming and quoting rules for the IoTDB table-model schema of the timeseries adapter.
 * <p>
 * Every identifier the adapter emits is built from {@code [a-z0-9_]} only, and every user-supplied
 * string (Thing ID, path, tag value) is emitted as a single-quoted literal through {@link #literal(String)}.
 * IoTDB identifiers are case-insensitive, so tag keys are encoded injectively into lower case.
 */
@Immutable
final class IoTDbSql {

    static final String COL_TIME = "time";
    static final String COL_THING_ID = "thing_id";
    static final String COL_PATH = "path";
    static final String COL_VALUE_DOUBLE = "value_double";
    static final String COL_VALUE_TEXT = "value_text";
    static final String COL_VALUE_BOOL = "value_bool";
    static final String COL_REVISION = "revision";
    static final String COL_UNIT = "unit";
    static final String COL_REVISION_SLOT = "rev_slot";

    static final String TAG_COLUMN_PREFIX = "tg_";

    /**
     * Origin of the bucket grid: {@code 2000-01-01T00:00:00Z}, the reference point of the kernel's
     * {@code bucketStart} (and of MongoDB's {@code $dateTrunc}), so buckets line up across backends.
     */
    static final long BUCKET_ORIGIN_MILLIS = Instant.parse("2000-01-01T00:00:00Z").toEpochMilli();

    private IoTDbSql() {
        throw new AssertionError();
    }

    /**
     * The type-specific columns holding a tag's value. IoTDB TAG columns are string-only and identify a device,
     * so ingest-time tags are FIELD columns, one per JSON scalar type.
     */
    enum TagType {
        STRING('s', "STRING"),
        NUMBER('n', "DOUBLE"),
        BOOLEAN('b', "BOOLEAN");

        private final char suffix;
        private final String sqlType;

        TagType(final char suffix, final String sqlType) {
            this.suffix = suffix;
            this.sqlType = sqlType;
        }

        String getSqlType() {
            return sqlType;
        }

        static Optional<TagType> forSuffix(final char suffix) {
            for (final TagType type : values()) {
                if (type.suffix == suffix) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * @param prefix the configured table prefix.
     * @param namespace the Thing namespace.
     * @return the table holding the namespace's data points.
     */
    static String tableName(final String prefix, final String namespace) {
        return prefix + encode(namespace);
    }

    /**
     * @param tagKey the tag key as stored at ingest.
     * @param type the JSON scalar type.
     * @return the FIELD column holding values of {@code type} for {@code tagKey}.
     */
    static String tagColumn(final String tagKey, final TagType type) {
        return TAG_COLUMN_PREFIX + encode(tagKey) + "_" + type.suffix;
    }

    /**
     * Inverse of {@link #tagColumn(String, TagType)}.
     *
     * @param column a column name.
     * @return the tag key if {@code column} is a tag column.
     */
    static Optional<String> tagKeyOf(final String column) {
        final String lower = column.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(TAG_COLUMN_PREFIX) || lower.length() < TAG_COLUMN_PREFIX.length() + 2 ||
                lower.charAt(lower.length() - 2) != '_' || tagTypeOf(lower).isEmpty()) {
            return Optional.empty();
        }
        return decode(lower.substring(TAG_COLUMN_PREFIX.length(), lower.length() - 2));
    }

    static Optional<TagType> tagTypeOf(final String column) {
        return column.isEmpty() ? Optional.empty() : TagType.forSuffix(column.charAt(column.length() - 1));
    }

    /**
     * Encodes arbitrary text into {@code [a-z0-9_]}: lower-case letters and digits are kept, every other UTF-8
     * byte becomes {@code _} plus two lower-case hex digits. Injective, so distinct inputs never share a column.
     */
    static String encode(final String raw) {
        final StringBuilder sb = new StringBuilder(raw.length());
        for (final byte b : raw.getBytes(StandardCharsets.UTF_8)) {
            if ((b >= 'a' && b <= 'z') || (b >= '0' && b <= '9')) {
                sb.append((char) b);
            } else {
                sb.append('_').append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
        }
        return sb.toString();
    }

    static Optional<String> decode(final String encoded) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(encoded.length());
        for (int i = 0; i < encoded.length(); i++) {
            final char c = encoded.charAt(i);
            if (c == '_') {
                if (i + 2 >= encoded.length()) {
                    return Optional.empty();
                }
                final int hi = Character.digit(encoded.charAt(i + 1), 16);
                final int lo = Character.digit(encoded.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    return Optional.empty();
                }
                out.write((hi << 4) | lo);
                i += 2;
            } else {
                out.write(c);
            }
        }
        return Optional.of(out.toString(StandardCharsets.UTF_8));
    }

    /**
     * @param identifier an identifier built by this class.
     * @return the identifier in double quotes.
     * @throws IllegalArgumentException if {@code identifier} contains anything but {@code [a-z0-9_]}.
     */
    static String quote(final String identifier) {
        for (int i = 0; i < identifier.length(); i++) {
            final char c = identifier.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                throw new IllegalArgumentException("Not a safe identifier: <" + identifier + ">");
            }
        }
        return '"' + identifier + '"';
    }

    /**
     * @param value any text.
     * @return {@code value} as a single-quoted SQL string literal; a single quote is the only character IoTDB
     * interprets inside it and is escaped by doubling.
     */
    static String literal(final String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /**
     * @param value a finite number.
     * @return {@code value} as a DOUBLE literal.
     * @throws IllegalArgumentException if {@code value} is NaN or infinite.
     */
    static String literal(final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Not a finite number: " + value);
        }
        return Double.toString(value);
    }

    static String literal(final boolean value) {
        return value ? "true" : "false";
    }

    /**
     * @param instant a point in time.
     * @return the millisecond timestamp literal IoTDB compares the {@code time} column with.
     */
    static String literal(final Instant instant) {
        return Long.toString(instant.toEpochMilli());
    }

    /**
     * @param step a bucket width of whole seconds.
     * @return the {@code date_bin} expression bucketing {@code time} on the shared grid.
     */
    static String dateBin(final Duration step) {
        return "date_bin(" + step.toMillis() + "ms, " + COL_TIME + ", " + BUCKET_ORIGIN_MILLIS + ")";
    }

    /**
     * Converts an RQL {@code like} pattern ({@code *} any run, {@code ?} one character) into a SQL LIKE pattern
     * for use with {@code ESCAPE '\'}.
     */
    static String likePattern(final String rqlPattern) {
        final StringBuilder sb = new StringBuilder(rqlPattern.length() + 8);
        for (int i = 0; i < rqlPattern.length(); i++) {
            final char c = rqlPattern.charAt(i);
            switch (c) {
                case '*' -> sb.append('%');
                case '?' -> sb.append('_');
                case '%', '_', '\\' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * @param retention the retention, or {@code null} for none.
     * @return the value of the table's {@code TTL} property.
     */
    static String ttl(@Nullable final Duration retention) {
        return retention == null ? "'INF'" : Long.toString(retention.toMillis());
    }
}
