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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.base.model.common.DittoDuration}.
 */
public final class DittoDurationTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoDuration.class,
                areImmutable(),
                provided("org.eclipse.ditto.base.model.common.DittoDuration$DittoTimeUnit").isAlsoImmutable());
    }

    @Test
    public void tryToGetInstanceFromNullDuration() {
        assertThatNullPointerException()
                .isThrownBy(() -> DittoDuration.of(null))
                .withMessage("The duration must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceFromNegativeDuration() {
        final Duration javaDuration = Duration.ofSeconds(-5);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> DittoDuration.of(javaDuration))
                .withMessage("The duration must not be negative but was <%s>!", javaDuration)
                .withNoCause();
    }

    @Test
    public void getInstanceFromPositiveDuration() {
        final Duration javaDuration = Duration.ofMinutes(1);

        final DittoDuration underTest = DittoDuration.of(javaDuration);

        assertThat(underTest.getDuration()).isEqualTo(javaDuration);
    }

    @Test
    public void getInstanceFromZeroDuration() {
        final Duration javaDuration = Duration.ofSeconds(0);

        final DittoDuration underTest = DittoDuration.of(javaDuration);

        assertThat(underTest.getDuration()).isEqualTo(javaDuration);
    }

    @Test
    public void createDittoDurationFromString() {
        final byte durationValue = 42;
        final DittoDuration dittoDuration = DittoDuration.parseDuration(String.valueOf(durationValue));

        assertThat(dittoDuration.getDuration()).isEqualTo(Duration.ofSeconds(durationValue));
    }

    @Test
    public void createDittoDurationFromStringSeconds() {
        final byte durationValue = 53;
        final DittoDuration dittoDuration = DittoDuration.parseDuration(durationValue + "s");

        assertThat(dittoDuration.getDuration()).isEqualTo(Duration.ofSeconds(durationValue));
    }

    @Test
    public void createDittoDurationFromStringMinutes() {
        final byte durationValue = 10;
        final DittoDuration dittoDuration = DittoDuration.parseDuration(durationValue + "m");

        assertThat(dittoDuration.getDuration()).isEqualTo(Duration.ofMinutes(durationValue));
    }

    @Test
    public void createDittoDurationFromStringMilliseconds() {
        final short durationValue = 763;
        final DittoDuration dittoDuration = DittoDuration.parseDuration(durationValue + "ms");

        assertThat(dittoDuration.getDuration()).isEqualTo(Duration.ofMillis(durationValue));
    }

    @Test
    public void createDittoDurationFromStringWithAndWithoutSecondsIsEqual() {
        final byte durationValue = 23;
        final DittoDuration dittoDuration1 = DittoDuration.parseDuration(String.valueOf(durationValue));
        final DittoDuration dittoDuration2 = DittoDuration.parseDuration(durationValue + "s");

        assertThat(dittoDuration1.getDuration()).isEqualTo(dittoDuration2.getDuration());
    }

    @Test
    public void tryToParseEmptyString() {
        assertThatExceptionOfType(NumberFormatException.class)
                .isThrownBy(() -> DittoDuration.parseDuration(""))
                .withMessageContaining("\"\"")
                .withNoCause();
    }

    @Test
    public void toStringReturnsExpected() {
        final String durationMillisecondsString = 5500 + "ms";
        final String durationSecondsString = 60 + "s";
        final String durationSecondsImplicitString = 23 + "";
        final String durationMinutesString = 2 + "m";
        final DittoDuration durationFromSeconds = DittoDuration.parseDuration(durationSecondsString);
        final DittoDuration durationFromSecondsImplicit = DittoDuration.parseDuration(durationSecondsImplicitString);
        final DittoDuration durationFromMilliseconds = DittoDuration.parseDuration(durationMillisecondsString);
        final DittoDuration durationFromMinutes = DittoDuration.parseDuration(durationMinutesString);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(durationFromSeconds.toString())
                    .as("seconds string")
                    .isEqualTo(durationSecondsString);
            softly.assertThat(durationFromSecondsImplicit.toString())
                    .as("seconds implicit string")
                    .isEqualTo(durationSecondsImplicitString);
            softly.assertThat(durationFromMilliseconds.toString())
                    .as("milliseconds string")
                    .isEqualTo(durationMillisecondsString);
            softly.assertThat(durationFromMinutes.toString())
                    .as("minutes string")
                    .isEqualTo(durationMinutesString);
        }
    }

    @Test
    public void isZeroReturnsExpected() {
        final DittoDuration zeroDuration = DittoDuration.parseDuration("0");
        final DittoDuration nonZeroDuration = DittoDuration.parseDuration("42");

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(zeroDuration.isZero())
                    .as("duration with zero length")
                    .isTrue();
            softly.assertThat(nonZeroDuration.isZero())
                    .as("duration with non-zero length")
                    .isFalse();
        }
    }

    @Test
    public void parseDurationWithPositivePrefix() {
        final byte durationValue = 42;
        final DittoDuration dittoDuration = DittoDuration.parseDuration(String.format("+%ds", durationValue));

        assertThat(dittoDuration.getDuration()).isEqualTo(Duration.ofSeconds(durationValue));
    }

    @Test
    public void tryToParseDurationWithNegativeAmountAndSuffix() {
        final String durationString = "-15s";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> DittoDuration.parseDuration(durationString))
                .withMessage("The duration must not be negative but was <%s>!", durationString)
                .withNoCause();
    }

    @Test
    public void tryToParseDurationWithNegativeAmountWithoutSuffix() {
        final String durationString = "-15";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> DittoDuration.parseDuration(durationString))
                .withMessage("The duration must not be negative but was <%s>!", durationString)
                .withNoCause();
    }

    @Test
    public void testSetAmount() {
        final DittoDuration hours = DittoDuration.parseDuration("1h");
        final DittoDuration minutes = DittoDuration.parseDuration("1m");
        final DittoDuration seconds = DittoDuration.parseDuration("1s");
        final DittoDuration millis = DittoDuration.parseDuration("1ms");

        final Duration duration = Duration.ofHours(10).plus(Duration.ofMillis(2030));

        assertThat(hours.setAmount(duration)).isEqualTo(DittoDuration.parseDuration("10h"));
        assertThat(minutes.setAmount(duration)).isEqualTo(DittoDuration.parseDuration("600m"));
        assertThat(seconds.setAmount(duration)).isEqualTo(DittoDuration.parseDuration("36002s"));
        assertThat(millis.setAmount(duration)).isEqualTo(DittoDuration.parseDuration("36002030ms"));
    }

}
