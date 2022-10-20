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
package org.eclipse.ditto.internal.utils.metrics.instruments.timer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.assertj.core.data.Percentage;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.junit.Before;
import org.junit.Test;

public final class PreparedKamonTimerTest {

    private PreparedTimer underTest;

    @Before
    public void setup() {
        underTest = PreparedKamonTimer.newTimer("TestTimer");
        underTest.reset();
    }

    @Test
    public void getRecords() {
        underTest.record(1, TimeUnit.SECONDS);
        underTest.record(5, TimeUnit.NANOSECONDS);

        final Long totalTime = underTest.getTotalTime();

        final long expectedTotalTime = TimeUnit.SECONDS.toNanos(1) + TimeUnit.NANOSECONDS.toNanos(5);
        assertThat(totalTime).isCloseTo(expectedTotalTime, Percentage.withPercentage(1));
    }

    @Test
    public void getRecordsDoesNotResetRecords() {
        underTest.record(1, TimeUnit.SECONDS);
        assertThat(underTest.getTotalTime()).isPositive();
        assertThat(underTest.getTotalTime()).isPositive();
    }

    @Test
    public void reset() {
        underTest.record(1, TimeUnit.SECONDS);
        assertThat(underTest.getTotalTime()).isCloseTo(TimeUnit.SECONDS.toNanos(1), Percentage.withPercentage(1));
        underTest.reset();
        assertThat(underTest.getTotalTime()).isZero();
    }

    @Test
    public void taggingWorks() {
        final var stringTag = Tag.of("stringTag", "2");
        final var booleanTag = Tag.of("booleanTag", true);

        underTest.tag(stringTag);
        underTest.tag(booleanTag);

        assertThat(underTest.getTagSet()).containsOnly(stringTag, booleanTag);
    }

    @Test
    public void startedTimerHasSameNameAndExpectedTags() {
        final var testTag = Tag.of("TEST", "someValue");
        underTest.tag(testTag);
        final var startedTimer = underTest.start();

        assertThat(startedTimer.getName()).as("name").isEqualTo(underTest.getName());
        assertThat(startedTimer.getTagSet())
                .as("tags")
                .containsOnly(testTag, Tag.of("segment", "overall"));
    }

    @Test
    public void canStartMultipleTimes() {
        final var started1 = underTest.start();
        final var started2 = underTest.start();

        assertThat(started1.getStartInstant()).isNotEqualTo(started2.getStartInstant());
    }

}
