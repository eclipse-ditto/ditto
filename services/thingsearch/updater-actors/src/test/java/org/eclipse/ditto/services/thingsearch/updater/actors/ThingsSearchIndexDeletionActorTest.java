/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.updater.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.Test;

/**
 * Unit Tests for {@link ThingsSearchIndexDeletionActor}.
 */
public class ThingsSearchIndexDeletionActorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    public void testInitialDelayCalculationInTheFuture1() {
        final Instant now = getInstantAtHourAndMinute(9, 42);
        final int firstIntervalHour = 10;

        final Duration expectedDuration = Duration.ofMinutes(18);

        assertThat(ThingsSearchIndexDeletionActor.calculateInitialDelay(now, firstIntervalHour))
                .isEqualByComparingTo(expectedDuration);
    }

    @Test
    public void testInitialDelayCalculationInTheFuture2() {
        final Instant now = getInstantAtHourAndMinute(10, 1);
        final int firstIntervalHour = 11;

        final Duration expectedDuration = Duration.ofMinutes(59);

        assertThat(ThingsSearchIndexDeletionActor.calculateInitialDelay(now, firstIntervalHour))
                .isEqualByComparingTo(expectedDuration);
    }

    @Test
    public void testInitialDelayCalculationInTheFutureNextDay1() {
        final Instant now = getInstantAtHourAndMinute(20, 1);
        final int firstIntervalHour = 2;

        final Duration expectedDuration = Duration.ofHours(5).plusMinutes(59);

        assertThat(ThingsSearchIndexDeletionActor.calculateInitialDelay(now, firstIntervalHour))
                .isEqualByComparingTo(expectedDuration);
    }

    @Test
    public void testInitialDelayCalculationCornerCase1() {
        final Instant now = getInstantAtHourAndMinute(0, 0);
        final int firstIntervalHour = 0;

        final Duration expectedDuration = Duration.ofMillis(0);

        assertThat(ThingsSearchIndexDeletionActor.calculateInitialDelay(now, firstIntervalHour))
                .isEqualByComparingTo(expectedDuration);
    }

    @Test
    public void testInitialDelayCalculationCornerCase2() {
        final Instant now = getInstantAtHourAndMinute(23, 1);
        final int firstIntervalHour = 23;

        final Duration expectedDuration = Duration.ofHours(24).minusMinutes(1);

        assertThat(ThingsSearchIndexDeletionActor.calculateInitialDelay(now, firstIntervalHour))
                .isEqualByComparingTo(expectedDuration);
    }

    private static Instant getInstantAtHourAndMinute(final int hour, final int minute) {
        final LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.now(), UTC)
                .withHour(hour)
                .withMinute(minute);
        return dateTime.atZone(UTC).toInstant();
    }
}
