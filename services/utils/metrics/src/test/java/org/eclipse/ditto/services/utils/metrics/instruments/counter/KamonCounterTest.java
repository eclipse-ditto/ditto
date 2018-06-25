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
package org.eclipse.ditto.services.utils.metrics.instruments.counter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

public class KamonCounterTest {

    private Counter sut;

    @Before
    public void setup() {
        sut = KamonCounter.newCounter("TestCounter");
        sut.reset();
    }

    @Test
    public void getCount() {
        sut.increment();
        assertThat(sut.getCount()).isEqualTo(1);
    }

    @Test
    public void getCountDoesNotResetCounter() {
        sut.increment();
        assertThat(sut.getCount()).isEqualTo(1);
        sut.increment();
        assertThat(sut.getCount()).isEqualTo(2);
    }

    @Test
    public void reset() {
        assertThat(sut.getCount()).isEqualTo(0);
        sut.increment();
        assertThat(sut.getCount()).isEqualTo(1);
        sut.reset();
        assertThat(sut.getCount()).isEqualTo(0);
    }
}