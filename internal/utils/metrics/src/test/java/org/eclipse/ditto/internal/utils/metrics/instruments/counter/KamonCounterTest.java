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
package org.eclipse.ditto.internal.utils.metrics.instruments.counter;

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
        assertThat(sut.getCount()).isZero();
        sut.increment();
        assertThat(sut.getCount()).isEqualTo(1);
        sut.reset();
        assertThat(sut.getCount()).isZero();
    }
}
