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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.data.TemporalUnitLessThanOffset;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link ImmutableTimePlaceholder}.
 */
public final class ImmutableTimePlaceholderTest {

    private static final ImmutableTimePlaceholder UNDER_TEST = ImmutableTimePlaceholder.INSTANCE;
    private static final Object SOME_OBJECT = new Object();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableTimePlaceholder.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testReplaceCurrentTimestampIso() {
        testWithTimePlaceholder("now", Instant.now());
    }

    @Test
    public void testReplaceCurrentTimestampIsoMinusDays() {
        testWithTimePlaceholder("now-2d", Instant.now().minus(Duration.ofDays(2)));
    }

    @Test
    public void testReplaceCurrentTimestampIsoPlusHours() {
        testWithTimePlaceholder("now+8h", Instant.now().plus(Duration.ofHours(8)));
    }

    @Test
    public void testReplaceCurrentTimestampIsoMinusSeconds() {
        testWithTimePlaceholder("now-45s", Instant.now().minus(Duration.ofSeconds(45)));
    }

    @Test
    public void testReplaceCurrentTimestampIsoTruncateDay() {
        testWithTimePlaceholder("now[d]", Instant.now().truncatedTo(ChronoUnit.DAYS));
    }

    @Test
    public void testReplaceCurrentTimestampIsoTruncateMinute() {
        testWithTimePlaceholder("now[m]", Instant.now().truncatedTo(ChronoUnit.MINUTES));
    }

    @Test
    public void testReplaceCurrentTimestampIsoMinusDaysTruncateDay() {
        testWithTimePlaceholder("now-2d[d]", Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.DAYS));
    }

    @Test
    public void testReplaceCurrentTimestampIsoPlusHoursTruncateMinute() {
        testWithTimePlaceholder("now+4h[m]", Instant.now().plus(Duration.ofHours(4)).truncatedTo(ChronoUnit.MINUTES));
    }

    @Test
    public void testReplaceCurrentTimestampIsoTruncateWeek() {
        testWithTimePlaceholder("now[w]",
                ZonedDateTime.now(ZoneId.systemDefault())
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS)
                        .toInstant()
        );
    }

    @Test
    public void testReplaceCurrentTimestampIsoTruncateMonth() {
        testWithTimePlaceholder("now[mo]",
                ZonedDateTime.now(ZoneId.systemDefault())
                        .with(TemporalAdjusters.firstDayOfMonth()).truncatedTo(ChronoUnit.DAYS)
                        .toInstant()
        );
    }

    @Test
    public void testReplaceCurrentTimestampIsoTruncateYear() {
        testWithTimePlaceholder("now[y]",
                ZonedDateTime.now(ZoneId.systemDefault())
                        .with(TemporalAdjusters.firstDayOfYear()).truncatedTo(ChronoUnit.DAYS)
                        .toInstant()
        );
    }

    @Test
    public void testReplaceCurrentTimestampMillisSinceEpoch() {
        testEpochWithTimePlaceholder("now_epoch_millis", Instant.now());
    }

    @Test
    public void testReplaceCurrentTimestampMillisSinceEpochPlusDay() {
        testEpochWithTimePlaceholder("now_epoch_millis+1d", Instant.now().plus(Duration.ofDays(1)));
    }

    @Test
    public void testReplaceCurrentTimestampMillisSinceEpochMinusMinutes() {
        testEpochWithTimePlaceholder("now_epoch_millis-25m", Instant.now().minus(Duration.ofMinutes(25)));
    }

    @Test
    public void testReplaceCurrentTimestampMillisSinceEpochTruncateHour() {
        testEpochWithTimePlaceholder("now_epoch_millis[h]", Instant.now().truncatedTo(ChronoUnit.HOURS));
    }

    @Test
    public void testReplaceCurrentTimestampMillisSinceEpochTruncateMillisecond() {
        testEpochWithTimePlaceholder("now_epoch_millis[ms]", Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    public void testReplaceCurrentTimestampMillisSinceEpochPlusDaysTruncateDay() {
        testWithTimePlaceholder("now+30d[d]", Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.DAYS));
    }

    @Test
    public void testReplaceCurrentTimestampMillisSinceEpochMinusSecondsTruncateHour() {
        testWithTimePlaceholder("now-30s[h]", Instant.now().minus(Duration.ofSeconds(4)).truncatedTo(ChronoUnit.HOURS));
    }

    private static void testWithTimePlaceholder(final String placeholder, final Instant expectedTimestamp) {
        final List<Instant> resolved = UNDER_TEST.resolveValues(SOME_OBJECT, placeholder).stream()
                .map(Instant::parse)
                .collect(Collectors.toList());
        assertThat(resolved)
                .hasSize(1)
                .allSatisfy(i ->
                        assertThat(i)
                                .isCloseTo(expectedTimestamp, new TemporalUnitLessThanOffset(1000, ChronoUnit.MILLIS))
        );
    }

    private static void testEpochWithTimePlaceholder(final String placeholder, final Instant expectedTimestamp) {
        final List<Instant> resolved = UNDER_TEST.resolveValues(SOME_OBJECT, placeholder).stream()
                .map(Long::parseLong)
                .map(Instant::ofEpochMilli)
                .collect(Collectors.toList());
        assertThat(resolved)
                .hasSize(1)
                .allSatisfy(i ->
                        assertThat(i)
                                .isCloseTo(expectedTimestamp, new TemporalUnitLessThanOffset(1000, ChronoUnit.MILLIS))
        );
    }

}
