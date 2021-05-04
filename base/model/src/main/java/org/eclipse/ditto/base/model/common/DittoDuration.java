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
package org.eclipse.ditto.base.model.common;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Representation of a string based duration with a positive amount.
 *
 * @since 2.0.0
 */
@Immutable
public final class DittoDuration implements CharSequence {

    private final long amount;
    private final DittoTimeUnit dittoTimeUnit;

    private DittoDuration(final long amount, final DittoTimeUnit dittoTimeUnit) {
        this.amount = amount;
        this.dittoTimeUnit = dittoTimeUnit;
    }

    /**
     * Creates a DittoDuration from the passed Java Duration.
     *
     * @param duration the <em>positive</em> Java Duration to let the DittoDuration base on.
     * @return the created DittoDuration.
     * @throws NullPointerException if {@code duration} is {@code null}.
     * @throws IllegalArgumentException if {@code duration} is negative.
     */
    public static DittoDuration of(final Duration duration) {
        checkNotNull(duration, "duration");
        checkArgument(duration, d -> !d.isNegative(),
                () -> MessageFormat.format("The duration must not be negative but was <{0}>!", duration));

        return new DittoDuration(duration.toMillis(), DittoTimeUnit.MILLISECONDS);
    }

    /**
     * Creates a DittoDuration from the passed char sequence that represents a <em>positive</em> amount.
     * Suffixes of the duration amount are allowed to specify the time unit:
     * <ul>
     *     <li>{@code "ms":} milliseconds, e. g. {@code "2000ms"} for 2000 milliseconds.</li>
     *     <li>{@code "s":} seconds, e. g. {@code "2s"} or {@code "2"} for 2 seconds.</li>
     *     <li>{@code "m":} minutes e. g. {@code "3m"} for 3 minutes.</li>
     * </ul>
     * <em>A string representation of a long value without suffix is interpreted as seconds.</em>
     *
     * @param duration the CharSequence containing the DittoDuration representation to be parsed.
     * @return the created DittoDuration.
     * @throws NullPointerException if {@code duration} is {@code null}.
     * @throws IllegalArgumentException if the duration char sequence does not contain a parsable {@code long} or
     * the parsed duration is not negative.
     */
    public static DittoDuration parseDuration(final CharSequence duration) {
        return parseDuration(checkNotNull(duration, "duration"), DittoTimeUnit.values());
    }

    private static DittoDuration parseDuration(final CharSequence duration, final DittoTimeUnit[] dittoTimeUnits) {
        DittoTimeUnit timeUnit = null;
        Long durationValue = null;
        int i = 0;
        while (null == durationValue && i < dittoTimeUnits.length) {
            timeUnit = dittoTimeUnits[i];
            durationValue = parseDurationRegexBased(duration, timeUnit);
            i++;
        }
        if (null == durationValue) {
            // implicitly interpret duration as seconds if unit was omitted
            timeUnit = DittoTimeUnit.SECONDS_IMPLICIT;
            durationValue = parseDurationPlain(duration, timeUnit.getSuffix());
        }
        return new DittoDuration(durationValue, timeUnit);
    }

    @Nullable
    private static Long parseDurationRegexBased(final CharSequence duration, final DittoTimeUnit dittoTimeUnit) {
        Long result = null;
        final Matcher matcher = dittoTimeUnit.getRegexMatcher(duration);
        if (matcher.matches()) {
            result = parseDurationPlain(matcher.group("amount"), dittoTimeUnit.getSuffix());
        }
        return result;
    }

    private static Long parseDurationPlain(final CharSequence charSequence, final CharSequence timeUnitSuffix) {
        // throws NumberFormatException which is a subclass of IllegalArgumentException
        final long result = Long.parseLong(charSequence.toString());
        checkArgument(result, r -> 0 <= r, () -> {
            final String msgPattern = "The duration must not be negative but was <{0}{1}>!";
            return MessageFormat.format(msgPattern, charSequence, timeUnitSuffix);
        });
        return result;
    }

    /**
     * Indicates whether this duration is zero length.
     *
     * @return {@code true} if this duration has a total length equal to zero.
     */
    public boolean isZero() {
        return 0 == amount;
    }

    /**
     * Returns this DittoDuration as Java Duration.
     *
     * @return the Java Duration representation of this DittoDuration.
     */
    public Duration getDuration() {
        return dittoTimeUnit.getJavaDuration(amount);
    }

    /**
     * Returns the time unit of the duration.
     *
     * @return the time unit.
     */
    public ChronoUnit getChronoUnit() {
        return dittoTimeUnit.getChronoUnit();
    }

    /**
     * Set the duration according to a Java duration keeping the time unit.
     *
     * @param duration the duration.
     * @return the new duration with adjusted amount.
     */
    public DittoDuration setAmount(final Duration duration) {
        final Duration unit = dittoTimeUnit.getChronoUnit().getDuration();
        final long seconds = duration.getSeconds();
        final long nanoseconds = duration.getNano();
        final long unitSeconds = unit.getSeconds();
        final long unitNanoseconds = unit.getNano();
        final long localAmount;
        if (unitSeconds != 0) {
            localAmount = Math.max(1L, seconds / unitSeconds);
        } else {
            final long withOverflow = seconds * (1_000_000_000L / unitNanoseconds) + (nanoseconds / unitNanoseconds);
            localAmount = Math.max(1L, withOverflow);
        }
        return new DittoDuration(localAmount, dittoTimeUnit);
    }

    @Override
    public int length() {
        return toString().length();
    }

    @Override
    public char charAt(final int index) {
        return toString().charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return toString().subSequence(start, end);
    }

    @Override
    public String toString() {
        return amount + dittoTimeUnit.getSuffix();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoDuration that = (DittoDuration) o;
        return amount == that.amount && dittoTimeUnit == that.dittoTimeUnit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, dittoTimeUnit);
    }

    private enum DittoTimeUnit {

        // The order matters as we expect seconds to be the main unit.
        // By making it the first constant, parsing a duration from string will be accelerated.
        SECONDS("s", Duration::ofSeconds, ChronoUnit.SECONDS),
        SECONDS_IMPLICIT("", Duration::ofSeconds, ChronoUnit.SECONDS),
        MILLISECONDS("ms", Duration::ofMillis, ChronoUnit.MILLIS),
        MINUTES("m", Duration::ofMinutes, ChronoUnit.MINUTES),
        HOURS("h", Duration::ofHours, ChronoUnit.HOURS);

        private final String suffix;
        private final LongFunction<Duration> toJavaDuration;
        private final ChronoUnit chronoUnit;
        private final Pattern regexPattern;

        DittoTimeUnit(final String suffix, final LongFunction<Duration> toJavaDuration, final ChronoUnit chronoUnit) {
            this.suffix = suffix;
            this.toJavaDuration = toJavaDuration;
            this.chronoUnit = chronoUnit;
            regexPattern = Pattern.compile("(?<amount>[+-]?\\d++)(?<unit>" + suffix + ")");
        }

        private Matcher getRegexMatcher(final CharSequence duration) {
            return regexPattern.matcher(duration);
        }

        private String getSuffix() {
            return suffix;
        }

        private Duration getJavaDuration(final long amount) {
            return toJavaDuration.apply(amount);
        }

        private ChronoUnit getChronoUnit() {
            return chronoUnit;
        }

    }

}
