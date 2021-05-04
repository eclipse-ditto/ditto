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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import kamon.metric.Timer;

public class StoppedKamonTimerTest {

    private StoppedTimer sut;

    @Before
    public void setup() {
        sut = PreparedKamonTimer.newTimer("TestTimer").start().stop();
    }

    @Test
    public void getName() {
        assertThat(sut.getName()).isEqualTo("TestTimer");
    }

    @Test
    public void getDuration() {
        assertThat(sut.getDuration().toNanos()).isGreaterThan(0);
    }

    @Test
    public void getTag() {
        assertThat(sut.getTag("segment")).isEqualTo("overall");
    }

    @Test
    public void getTags() {
        assertThat(sut.getTags().keySet()).hasSize(1);
    }

    @Test
    public void reportDurationCorrectly() throws Exception {
        // GIVEN: 1250ms passes between timer start and timer end
        final StartedTimer startedTimer = PreparedKamonTimer.newTimer(UUID.randomUUID().toString()).start();
        TimeUnit.MILLISECONDS.sleep(1250);

        // WHEN: timer records the elapsed nanoseconds
        final StoppedKamonTimer stoppedTimer = (StoppedKamonTimer) startedTimer.stop();
        final kamon.metric.Timer.Atomic internalTimer = (Timer.Atomic) stoppedTimer.getKamonInternalTimer();

        // THEN: the recorded value is at least 1s
        assertThat(internalTimer.getMaxValueAsDouble()).isGreaterThan(1e9);
    }
}
