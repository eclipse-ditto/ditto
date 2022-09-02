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
package org.eclipse.ditto.internal.utils.metrics.instruments.histogram;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Percentage;
import org.junit.Before;
import org.junit.Test;

public class KamonHistogramTest {

    private Histogram sut;

    @Before
    public void setup() {
        sut = KamonHistogram.newHistogram("TestHistogram");
        sut.reset();
    }

    @Test
    public void record() {
        sut.record(4711L);
        final Long[] recordedValues = sut.getRecordedValues();
        assertThat(recordedValues).hasSize(1);
        assertThat(recordedValues[0]).isCloseTo(4711L, Percentage.withPercentage(1));
    }

    @Test
    public void getRecordsDoesNotResetHistogram() {
        sut.record(4711L);
        assertThat(sut.getRecordedValues()).hasSize(1);
        assertThat(sut.getRecordedValues()).hasSize(1);
    }

    @Test
    public void resets() {
        sut.record(4711L);
        assertThat(sut.getRecordedValues()).hasSize(1);
        sut.reset();
        assertThat(sut.getRecordedValues()).hasSize(0);
    }

}
