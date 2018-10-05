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
package org.eclipse.ditto.services.utils.metrics.instruments.histogram;

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
        assertThat(recordedValues.length).isEqualTo(1);
        assertThat(recordedValues[0]).isCloseTo(4711L, Percentage.withPercentage(1));
    }

    @Test
    public void getRecordsDoesNotResetHistogram() {
        sut.record(4711L);
        assertThat(sut.getRecordedValues().length).isEqualTo(1);
        assertThat(sut.getRecordedValues().length).isEqualTo(1);
    }

    @Test
    public void resets() {
        sut.record(4711L);
        assertThat(sut.getRecordedValues().length).isEqualTo(1);
        sut.reset();
        assertThat(sut.getRecordedValues().length).isEqualTo(0);
    }

}
