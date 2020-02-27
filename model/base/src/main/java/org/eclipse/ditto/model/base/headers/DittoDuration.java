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
package org.eclipse.ditto.model.base.headers;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Package internal representation of a string based duration with a positive amount.
 *
 * @since 1.1.0
 */
@Immutable
final class DittoDuration implements CharSequence {

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
    static DittoDuration of(final Duration duration) {
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
     * @throws java.lang.NumberFormatException if the duration char sequence does not contain a parsable {@code long}.
     * @throws IllegalArgumentException if the parsed duration is not negative.
     */
    static DittoDuration parseDuration(final CharSequence duration) {
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

            // interpret duration as seconds if unit was omitted
            timeUnit = DittoTimeUnit.SECONDS;
            durationValue = parseDurationPlain(duration, "");
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
    boolean isZero() {
        return 0 == amount;
    }

    /**
     * Returns this DittoDuration as Java Duration.
     *
     * @return the Java Duration representation of this DittoDuration.
     */
    Duration getDuration() {
        return dittoTimeUnit.getJavaDuration(amount);
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
        SECONDS("s", Duration::ofSeconds),
        MILLISECONDS("ms", Duration::ofMillis),
        MINUTES("m", Duration::ofMinutes);

        private final String suffix;
        private final LongFunction<Duration> toJavaDuration;
        private final Pattern regexPattern;

        private DittoTimeUnit(final String suffix, final LongFunction<Duration> toJavaDuration) {
            this.suffix = suffix;
            this.toJavaDuration = toJavaDuration;
            regexPattern = Pattern.compile("(?<amount>[+-]?\\d+)(?<unit>" + suffix + ")");
        }

        public Matcher getRegexMatcher(final CharSequence duration) {
            return regexPattern.matcher(duration);
        }

        public String getSuffix() {
            return suffix;
        }

        public Duration getJavaDuration(final long amount) {
            return toJavaDuration.apply(amount);
        }

    }

}
