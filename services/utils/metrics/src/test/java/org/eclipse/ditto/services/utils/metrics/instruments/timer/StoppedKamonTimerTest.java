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

import org.junit.Before;
import org.junit.Test;

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
}
