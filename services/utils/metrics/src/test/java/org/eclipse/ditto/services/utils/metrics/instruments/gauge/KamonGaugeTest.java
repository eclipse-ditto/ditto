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
package org.eclipse.ditto.services.utils.metrics.instruments.gauge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

public class KamonGaugeTest {

    private Gauge sut;

    @Before
    public void setup() {
        sut = KamonGauge.newGauge("TestGauge");
        sut.reset();
    }

    @Test
    public void setAndGet() {
        sut.set(9L);
        assertThat(sut.get()).isEqualTo(9L);
    }

    @Test
    public void reset() {
        sut.set(5L);
        assertThat(sut.get()).isEqualTo(5L);
        sut.reset();
        assertThat(sut.get()).isEqualTo(0L);
    }

    @Test
    public void increment() {
        sut.set(5L);
        assertThat(sut.get()).isEqualTo(5L);
        sut.increment();
        assertThat(sut.get()).isEqualTo(6L);
    }

    @Test
    public void decrement() {
        sut.set(5L);
        assertThat(sut.get()).isEqualTo(5L);
        sut.decrement();
        assertThat(sut.get()).isEqualTo(4L);
    }
}
