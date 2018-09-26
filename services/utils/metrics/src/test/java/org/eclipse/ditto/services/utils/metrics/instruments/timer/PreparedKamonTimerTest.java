/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.metrics.instruments.timer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.assertj.core.data.Percentage;
import org.junit.Before;
import org.junit.Test;

public class PreparedKamonTimerTest {

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
        final Long[] records = sut.getRecords();
        assertThat(records.length).isEqualTo(2);
        assertThat(records[1]).isCloseTo(TimeUnit.SECONDS.toNanos(1), Percentage.withPercentage(1));
        assertThat(records[0]).isCloseTo(TimeUnit.NANOSECONDS.toNanos(5), Percentage.withPercentage(1));
    }

    @Test
    public void getRecordsDoesNotResetRecords() {
        sut.record(1, TimeUnit.SECONDS);
        assertThat(sut.getRecords().length).isEqualTo(1);
        assertThat(sut.getRecords().length).isEqualTo(1);
    }

    @Test
    public void reset() {
        sut.record(1, TimeUnit.SECONDS);
        assertThat(sut.getRecords().length).isEqualTo(1);
        sut.reset();
        assertThat(sut.getRecords().length).isEqualTo(0);
    }

    @Test
    public void taggingWorks() {
        sut.tag("stringTag", "2");
        sut.tag("longTag", 2L);
        sut.tag("booleanTag", true);
        sut.tag("doubleTag", 2.0);

        assertThat(sut.getTags()).hasSize(4);
        assertThat(sut.getTag("stringTag")).isEqualTo("2");
        assertThat(sut.getTag("longTag")).isEqualTo("2");
        assertThat(sut.getTag("booleanTag")).isEqualTo("true");
        assertThat(sut.getTag("doubleTag")).isEqualTo("2.0");
    }

    @Test
    public void startedTimerHasSameNameAndSameTags() {
        sut.tag("TEST", "someValue");
        final StartedTimer start = sut.start();
        assertThat(start.getTags().keySet()).hasSize(2);
        assertThat(start.getTag("segment")).isEqualTo("overall");
        assertThat(start.getTag("TEST")).isEqualTo("someValue");
        assertThat(start.getName()).isEqualTo(sut.getName());
    }

    @Test
    public void canStartMultipleTimes() {
        final StartedTimer started1 = sut.start();
        final StartedTimer started2 = sut.start();
        assertThat(started1.getStartTimeStamp()).isNotEqualTo(started2.getStartTimeStamp());
    }
}
