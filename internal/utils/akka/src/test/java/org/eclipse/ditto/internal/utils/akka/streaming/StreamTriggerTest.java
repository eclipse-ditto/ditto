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
package org.eclipse.ditto.internal.utils.akka.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link StreamTrigger}.
 */
public class StreamTriggerTest {

    private static final Duration VERY_LONG_DURATION = Duration.ofHours(10);
    private static final Instant NOW = Instant.now();
    private static final Duration MIN_START_OFFSET = Duration.ofMinutes(15);
    private static final Duration STREAM_INTERVAL = Duration.ofMinutes(5);

    @Test
    public void assertImmutability() {
        assertInstancesOf(StreamTrigger.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(StreamTrigger.class).verify();
    }


    @Test
    public void reschedule() {
        final StreamTrigger initial = StreamTrigger.of(NOW.minusSeconds(2000), NOW.minusSeconds(1000), NOW);

        final Instant rescheduledPlannedStreamStart = NOW.plusSeconds(60);
        final StreamTrigger rescheduled = initial.rescheduleAt(rescheduledPlannedStreamStart);

        final StreamTrigger expectedRescheduled =
                StreamTrigger.of(initial.getQueryStart(), initial.getQueryEnd(), rescheduledPlannedStreamStart);
        assertThat(rescheduled).isEqualTo(expectedRescheduled);
    }

    @Test
    public void calculateTriggerWithLastEndLongTimeInThePast() {
        final Instant inThePast = NOW.minus(VERY_LONG_DURATION);

        final StreamTrigger actual = calculateTrigger(NOW, inThePast, MIN_START_OFFSET, STREAM_INTERVAL);

        final StreamTrigger expected = createExpectedStreamTrigger(inThePast, NOW);
        assertThat(actual).isEqualTo(expected);
    }

    /**
     * Tests with last end in the past, with the restriction that it is less than "start-offset" in the past.
     */
    @Test
    public void calculateTriggerWithLastEndInThePastInsideStartOffset() {
        final Duration beforeNow = MIN_START_OFFSET.minusSeconds(2);
        //assumption:
        assertThat(beforeNow).isGreaterThan(Duration.ZERO);
        final Instant inThePast = NOW.minus(beforeNow);

        final StreamTrigger actual = calculateTrigger(NOW, inThePast, MIN_START_OFFSET, STREAM_INTERVAL);

        final StreamTrigger expected = createExpectedStreamTrigger(inThePast,
                inThePast.plus(STREAM_INTERVAL).plus(MIN_START_OFFSET));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void calculateTriggerWithLastEndEqualToNow() {
        final StreamTrigger actual = calculateTrigger(NOW, NOW, MIN_START_OFFSET, STREAM_INTERVAL);

        final StreamTrigger expected = createExpectedStreamTrigger(NOW,
                NOW.plus(STREAM_INTERVAL).plus(MIN_START_OFFSET));
        assertThat(actual).isEqualTo(expected);
    }

    /**
     * Unlikely, but not impossible in case of a really huge clock drift.
     */
    @Test
    public void calculateTriggerWithLastEndLongTimeInTheFuture() {
        final Instant inTheFuture = NOW.plus(VERY_LONG_DURATION);

        final StreamTrigger actual = calculateTrigger(NOW, inTheFuture, MIN_START_OFFSET, STREAM_INTERVAL);

        final StreamTrigger expected = createExpectedStreamTrigger(inTheFuture,
                inTheFuture.plus(STREAM_INTERVAL).plus(MIN_START_OFFSET));
        assertThat(actual).isEqualTo(expected);
    }

    /**
     * Tests with last end in the future, with the restriction that it is less than "start-offset" in the future.
     */
    @Test
    public void calculateTriggerWithLastEndInTheFutureInsideStartOffset() {
        final Duration afterNow = MIN_START_OFFSET.minusSeconds(2);
        //assumption:
        assertThat(afterNow).isGreaterThan(Duration.ZERO);
        final Instant inTheFuture = NOW.plus(afterNow);

        final StreamTrigger actual = calculateTrigger(NOW, inTheFuture, MIN_START_OFFSET, STREAM_INTERVAL);

        final StreamTrigger expected = createExpectedStreamTrigger(inTheFuture,
                inTheFuture.plus(STREAM_INTERVAL).plus(MIN_START_OFFSET));
        assertThat(actual).isEqualTo(expected);
    }

    private static StreamTrigger calculateTrigger(final Instant now, final Instant queryStart,
            final Duration startOffset, final Duration streamInterval) {

        return StreamTrigger.calculateStreamTrigger(now, queryStart, startOffset, streamInterval);
    }

    private static StreamTrigger createExpectedStreamTrigger(final Instant queryStart,
            final Instant plannedStreamStart) {

        return StreamTrigger.of(queryStart, queryStart.plus(STREAM_INTERVAL), plannedStreamStart);
    }
}
