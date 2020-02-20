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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

/**
 * Package internal representation of a string based Duration optionally containing the unit {@value #MS_SUFFIX} for
 * milliseconds or {@value #S_SUFFIX} for seconds.
 *
 * @since 1.1.0
 */
final class DittoDuration implements CharSequence {

    private static final String MS_SUFFIX = "ms";
    private static final String S_SUFFIX = "s";
    private static final String M_SUFFIX = "m";

    private final Duration duration;
    private final String delegateString;

    private DittoDuration(final Duration duration, @Nullable final TimeUnit timeUnit) {
        this.duration = duration;
        delegateString = durationToString(duration, timeUnit);
    }

    /**
     * @return the wrapped Java Duration.
     */
    Duration getDuration() {
        return duration;
    }

    /**
     * Creates a DittoDuration from the passed Java Duration and the optional {@code timeUnit}. If timeUnit is
     * not specified, seconds are assumed.
     *
     * @param timeout the Java Duration to let the DittoDuration base on.
     * @param timeUnit the optional time unit (may be milliseconds or seconds).
     * @return the created DittoDuration.
     * @throws IllegalArgumentException in case TimeUnit is a unit different to milliseconds or seconds.
     */
    static DittoDuration fromDuration(final Duration timeout, @Nullable final TimeUnit timeUnit) {
        return new DittoDuration(timeout, timeUnit);
    }

    /**
     * Creates a DittoDuration from the passed {@code timeout} CharSequence interpreting the suffixes
     * {@value #MS_SUFFIX} as milliseconds and {@value #S_SUFFIX} as seconds.
     *
     * @param timeout the CharSequence to interpret as DittoDuration.
     * @return the created DittoDuration.
     * @throws java.lang.NumberFormatException if the timeout string does not contain a parsable {@code long}.
     */
    static DittoDuration fromTimeoutString(final CharSequence timeout) {

        final String timeoutStr = timeout.toString();
        if (timeoutStr.endsWith(MS_SUFFIX)) {
            final String timeoutMs = timeoutStr.substring(0, timeoutStr.lastIndexOf(MS_SUFFIX));
            return new DittoDuration(Duration.ofMillis(Long.parseLong(timeoutMs)), TimeUnit.MILLISECONDS);
        } else if (timeoutStr.endsWith(S_SUFFIX)) {
            final String timeoutS = timeoutStr.substring(0, timeoutStr.lastIndexOf(S_SUFFIX));
            return new DittoDuration(Duration.ofSeconds(Long.parseLong(timeoutS)), TimeUnit.SECONDS);
        } else if (timeoutStr.endsWith(M_SUFFIX)) {
            final String timeoutM = timeoutStr.substring(0, timeoutStr.lastIndexOf(M_SUFFIX));
            return new DittoDuration(Duration.ofMinutes(Long.parseLong(timeoutM)), TimeUnit.MINUTES);
        } else {
            // interpret timeout duration as seconds if unit was omitted:
            return new DittoDuration(Duration.ofSeconds(Long.parseLong(timeoutStr)), null);
        }
    }

    private String durationToString(final Duration duration, @Nullable final TimeUnit timeUnit) {

        if (null == timeUnit) {
            return String.valueOf(duration.getSeconds());
        }

        switch (timeUnit) {
            case MILLISECONDS:
                return duration.toMillis() + MS_SUFFIX;
            case SECONDS:
                return duration.getSeconds() + S_SUFFIX;
            case MINUTES:
                return duration.toMinutes() + M_SUFFIX;
            default:
                throw new IllegalArgumentException("Unsupported TimeUnit: " + timeUnit);
        }
    }

    @Override
    public int length() {
        return delegateString.length();
    }

    @Override
    public char charAt(final int index) {
        return delegateString.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return delegateString.subSequence(start, end);
    }

    @Override
    public String toString() {
        return delegateString;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoDuration that = (DittoDuration) o;
        return Objects.equals(duration, that.duration) &&
                Objects.equals(delegateString, that.delegateString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration, delegateString);
    }
}
