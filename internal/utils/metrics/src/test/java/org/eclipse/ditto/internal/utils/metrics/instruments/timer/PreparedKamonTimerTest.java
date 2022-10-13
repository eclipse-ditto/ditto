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
import org.junit.Before;
import org.junit.Test;

public final class PreparedKamonTimerTest {

    private PreparedTimer sut;

    @Before
    public void setup() {
        sut = PreparedKamonTimer.newTimer("TestTimer");
        sut.reset();
    }

    @Test
    public void getRecords() {
        sut.record(1, TimeUnit.SECONDS);
        sut.record(5, TimeUnit.NANOSECONDS);

        final Long totalTime = sut.getTotalTime();

        final long expectedTotalTime = TimeUnit.SECONDS.toNanos(1) + TimeUnit.NANOSECONDS.toNanos(5);
        assertThat(totalTime).isCloseTo(expectedTotalTime, Percentage.withPercentage(1));
    }

    @Test
    public void getRecordsDoesNotResetRecords() {
        sut.record(1, TimeUnit.SECONDS);
        assertThat(sut.getTotalTime()).isPositive();
        assertThat(sut.getTotalTime()).isPositive();
    }

    @Test
    public void reset() {
        sut.record(1, TimeUnit.SECONDS);
        assertThat(sut.getTotalTime()).isCloseTo(TimeUnit.SECONDS.toNanos(1), Percentage.withPercentage(1));
        sut.reset();
        assertThat(sut.getTotalTime()).isZero();
    }

    @Test
    public void taggingWorks() {
        sut.tag("stringTag", "2");
        sut.tag("longTag", 2L);
        sut.tag("booleanTag", true);
        sut.tag("doubleTag", 2.0);

        assertThat(sut.getTags()).hasSize(4);
        assertThat(sut.getTag("stringTag")).hasValue("2");
        assertThat(sut.getTag("longTag")).hasValue("2");
        assertThat(sut.getTag("booleanTag")).hasValue("true");
        assertThat(sut.getTag("doubleTag")).hasValue("2.0");
    }

    @Test
    public void startedTimerHasSameNameAndSameTags() {
        sut.tag("TEST", "someValue");
        final StartedTimer start = sut.start();

        assertThat(start.getTags().keySet()).hasSize(2);
        assertThat(start.getTag("segment")).hasValue("overall");
        assertThat(start.getTag("TEST")).hasValue("someValue");
        assertThat(start.getName()).isEqualTo(sut.getName());
    }

    @Test
    public void canStartMultipleTimes() {
        final var started1 = sut.start();
        final var started2 = sut.start();

        assertThat(started1.getStartInstant()).isNotEqualTo(started2.getStartInstant());
    }

}
