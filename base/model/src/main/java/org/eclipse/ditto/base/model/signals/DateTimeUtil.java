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
package org.eclipse.ditto.base.model.signals;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;

/**
 * ISO 8601 formatters.
 */
public final class DateTimeUtil {

    /**
     * ISO8601 calendar date and time with time zone designator, expanded format.
     */
    public static final DateTimeFormatter OFFSET_DATE_TIME_EXPANDED =
            appendOffsets("uuuu-MM-dd'T'HH[:mm[:ss[.SSS]]]", "+HH:mm", "+HH");

    /**
     * ISO8601 calendar date and time with time zone designator, basic format.
     */
    public static final DateTimeFormatter OFFSET_DATE_TIME_BASIC =
            appendOffsets("uuuuMMdd'T'HH[mm[ss[.SSS]]]", "+HHmm", "+HH");

    /**
     * ISO8601 calendar date and time with time zone designator for parsing.
     */
    public static final DateTimeFormatter OFFSET_DATE_TIME =
            caseInsensitiveAnd(or(OFFSET_DATE_TIME_BASIC, OFFSET_DATE_TIME_EXPANDED));

    /**
     * Creates an {@link java.time.OffsetDateTime} object from an ISO8601 calendar date and time with time zone designator.
     *
     * @param timestamp The timestamp string.
     * @return The {@code OffsetDateTime} object.
     * @throws java.time.DateTimeException If the timestamp is not a valid ISO8601 calendar date and time with time
     * zone designator and the mandatory separator {@code T}.
     */
    public static OffsetDateTime parseOffsetDateTime(final String timestamp) {
        return OffsetDateTime.parse(timestamp, DateTimeUtil.OFFSET_DATE_TIME);
    }

    /**
     * Specifies a date time format with several possible time zone designators.
     * <p>
     * Caution:
     * <p>
     * <ol>
     * <li>
     * Offset format patterns must be one of these: +HH, +HHmm, +HH:mm, +HHMM, +HH:MM, +HHMMss, +HH:MM:ss, +HHMMSS,
     * +HH:MM:SS.
     * </li>
     * <li>
     * The pattern +HH:mm disallows seconds. The pattern +HH:MM allows patterns but disallows milliseconds.
     * </li>
     * <li>
     * You MUST list offset patterns in the order of decreasing length---otherwise the longer patterns have no effect!
     * </li>
     * <li>
     * A no-offset string literal is always a part of the offset pattern. Z is our no-offset string.
     * </li>
     * </ol>
     *
     * @param dateTimeFormat Pattern string of the date-time part.
     * @param offsetPatterns Alternative pattern strings of the time zone designator.
     * @return A date-time-formatter that hopefully does what you want.
     */
    private static DateTimeFormatter appendOffsets(final String dateTimeFormat, final String... offsetPatterns) {
        final DateTimeFormatter dateTimePart = DateTimeFormatter.ofPattern(dateTimeFormat);
        final DateTimeFormatter[] formatters = Arrays.stream(offsetPatterns)
                .map(offsetPattern -> {
                    final DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
                    builder.append(dateTimePart);
                    builder.appendOffset(offsetPattern, "Z");
                    return builder.toFormatter();
                })
                .toArray(DateTimeFormatter[]::new);
        return or(formatters);
    }

    private static DateTimeFormatter or(final DateTimeFormatter... alternatives) {
        final DateTimeFormatterBuilder prefix = new DateTimeFormatterBuilder();
        for (final DateTimeFormatter alternative : alternatives) {
            prefix.optionalStart().append(alternative).optionalEnd();
        }
        return prefix.toFormatter();
    }

    private static DateTimeFormatter and(final DateTimeFormatter... formatters) {
        final DateTimeFormatterBuilder prefix = new DateTimeFormatterBuilder();
        for (final DateTimeFormatter formatter : formatters) {
            prefix.append(formatter);
        }
        return prefix.toFormatter();
    }

    private static DateTimeFormatter caseInsensitiveAnd(final DateTimeFormatter... formatters) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(and(formatters))
                .toFormatter();
    }

    private DateTimeUtil() {
        throw new AssertionError();
    }

}
