/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.placeholders;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.DittoDuration;

/**
 * Placeholder implementation that replaces:
 * <ul>
 * <li>{@code time:now} ->
 *     the current system timestamp in ISO-8601 format, e.g.: {@code "2021-11-17T09:44:08Z"}</li>
 * <li>{@code time:now_epoch_millis} ->
 *     the current system timestamp in milliseconds since the epoch of {@code 1970-01-01T00:00:00Z}</li>
 * </ul>
 * The input value is any Object and is not used.
 */
@Immutable
final class ImmutableTimePlaceholder implements TimePlaceholder {

    /**
     * Singleton instance of the ImmutableTimePlaceholder.
     */
    static final ImmutableTimePlaceholder INSTANCE = new ImmutableTimePlaceholder();

    private static final String NOW_PLACEHOLDER = "now";
    private static final String NOW_EPOCH_MILLIS_PLACEHOLDER = "now_epoch_millis";

    private static final String TRUNCATE_START;
    private static final String TRUNCATE_END = "]";

    static {
        TRUNCATE_START = "[";
    }

    private ImmutableTimePlaceholder() {
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public List<String> getSupportedNames() {
        return Collections.emptyList();
    }

    @Override
    public boolean supports(final String name) {
        return startsWithNowPrefix(name) && containsEmptyOrValidTimeRangeDefinition(name);
    }

    @Override
    public List<String> resolveValues(final Object someObject, final String placeholder) {
        ConditionChecker.argumentNotEmpty(placeholder, "placeholder");
        final Instant now = Instant.now();
        switch (placeholder) {
            case NOW_PLACEHOLDER:
                return Collections.singletonList(now.toString());
            case NOW_EPOCH_MILLIS_PLACEHOLDER:
                return Collections.singletonList(formatAsEpochMilli(now));
            default:
                return resolveWithPotentialTimeRangeSuffix(now, placeholder);
        }
    }

    private static boolean startsWithNowPrefix(final String name) {
        return name.startsWith(NOW_EPOCH_MILLIS_PLACEHOLDER) || name.startsWith(NOW_PLACEHOLDER) ;
    }

    private boolean containsEmptyOrValidTimeRangeDefinition(final String placeholder) {
        final String timeRangeSuffix = extractTimeRangeSuffix(placeholder);
        return timeRangeSuffix.isEmpty() || isValidTimeRange(timeRangeSuffix);
    }

    private static String formatAsEpochMilli(final Instant now) {
        return String.valueOf(now.toEpochMilli());
    }

    private static String extractTimeRangeSuffix(final String placeholder) {
        final String timeRangeSuffix;
        if (placeholder.startsWith(NOW_EPOCH_MILLIS_PLACEHOLDER)) {
            timeRangeSuffix = placeholder.replace(NOW_EPOCH_MILLIS_PLACEHOLDER, "");
        } else if (placeholder.startsWith(NOW_PLACEHOLDER)) {
            timeRangeSuffix = placeholder.replace(NOW_PLACEHOLDER, "");
        } else {
            throw new IllegalStateException("Unsupported placeholder prefix for TimePlaceholder: " + placeholder);
        }
        return timeRangeSuffix;
    }

    private boolean isValidTimeRange(final String timeRangeSuffix) {
        final char sign = timeRangeSuffix.charAt(0);
        if (sign == '-' || sign == '+') {
            final String durationWithTruncate = timeRangeSuffix.substring(1);
            final String durationString;
            if (durationWithTruncate.contains(TRUNCATE_START) && durationWithTruncate.contains(TRUNCATE_END)) {
                final String[] durationStringAndTruncateString = durationWithTruncate.split(TRUNCATE_START, 2);
                durationString = durationStringAndTruncateString[0];
                final String truncateString = durationStringAndTruncateString[1];
                if (!isValidTruncateStatement(truncateString.substring(0, truncateString.lastIndexOf(TRUNCATE_END)))) {
                    return false;
                }
            } else {
                durationString = durationWithTruncate;
            }
            try {
                DittoDuration.parseDuration(durationString);
                return true;
            } catch (final Exception e) {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean isValidTruncateStatement(final String truncateString) {
        return DittoDuration.DittoTimeUnit.forSuffix(truncateString).isPresent();
    }

    private List<String> resolveWithPotentialTimeRangeSuffix(final Instant now, final String placeholder) {
        final String timeRangeSuffix = extractTimeRangeSuffix(placeholder);
        if (timeRangeSuffix.isEmpty()) {
            return Collections.emptyList();
        }

        @Nullable final ChronoUnit truncateTo = calculateTruncateTo(timeRangeSuffix);

        final char sign = timeRangeSuffix.charAt(0);
        if (sign == '-') {
            final DittoDuration dittoDuration = extractTimeRangeDuration(timeRangeSuffix);
            Instant nowMinus = now.minus(dittoDuration.getDuration());
            if (truncateTo != null) {
                nowMinus = truncateInstantTo(nowMinus, truncateTo);
            }
            return buildResult(placeholder, nowMinus);
        } else if (sign == '+') {
            final DittoDuration dittoDuration = extractTimeRangeDuration(timeRangeSuffix);
            Instant nowPlus = now.plus(dittoDuration.getDuration());
            if (truncateTo != null) {
                nowPlus = truncateInstantTo(nowPlus, truncateTo);
            }
            return buildResult(placeholder, nowPlus);
        } else if (truncateTo != null) {
            final Instant nowTruncated = truncateInstantTo(now, truncateTo);
            return buildResult(placeholder, nowTruncated);
        }

        return Collections.emptyList();
    }

    private static Instant truncateInstantTo(final Instant instant, final ChronoUnit truncateTo) {
        switch (truncateTo) {
            case WEEKS:
                return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS)
                        .toInstant();
            case MONTHS:
                return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
                        .with(TemporalAdjusters.firstDayOfMonth()).truncatedTo(ChronoUnit.DAYS)
                        .toInstant();
            case YEARS:
                return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
                        .with(TemporalAdjusters.firstDayOfYear()).truncatedTo(ChronoUnit.DAYS)
                        .toInstant();
            default:
                return instant.truncatedTo(truncateTo);
        }
    }

    private static List<String> buildResult(final String placeholder, final Instant nowMinus) {
        if (placeholder.startsWith(NOW_EPOCH_MILLIS_PLACEHOLDER)) {
            return Collections.singletonList(formatAsEpochMilli(nowMinus));
        } else if (placeholder.startsWith(NOW_PLACEHOLDER)) {
            return Collections.singletonList(nowMinus.toString());
        } else {
            return Collections.emptyList();
        }
    }

    @Nullable
    private static ChronoUnit calculateTruncateTo(final String timeRangeSuffix) {
        if (timeRangeSuffix.contains(TRUNCATE_START) && timeRangeSuffix.contains(TRUNCATE_END)) {
            final String truncateUnit = timeRangeSuffix.substring(timeRangeSuffix.indexOf(TRUNCATE_START) + 1,
                    timeRangeSuffix.lastIndexOf(TRUNCATE_END));
            final DittoDuration.DittoTimeUnit dittoTimeUnit =
                    DittoDuration.DittoTimeUnit.forSuffix(truncateUnit).orElseThrow(() ->
                            new IllegalStateException("Truncating string contained unsupported unit: " + truncateUnit)
                    );
            return dittoTimeUnit.getChronoUnit();
        } else {
            return null;
        }
    }

    private static DittoDuration extractTimeRangeDuration(final String timeRangeSuffix) {
        final int truncateStart = timeRangeSuffix.indexOf(TRUNCATE_START);
        final String timeRange;
        if (truncateStart > 0) {
            timeRange = timeRangeSuffix.substring(0, truncateStart);
        } else {
            timeRange = timeRangeSuffix;
        }
        return DittoDuration.parseDuration(timeRange.substring(1));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
