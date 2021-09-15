/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.config.supervision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.Mockito;


public final class ExponentialBackOffTest {

    @Test
    public void initialIsMinDelay() {
        final ExponentialBackOffConfig exponentialBackOffConfig = Mockito.mock(ExponentialBackOffConfig.class);
        final Duration expected = Duration.ofSeconds(1234);
        when(exponentialBackOffConfig.getMin()).thenReturn(expected);
        when(exponentialBackOffConfig.getMax()).thenReturn(expected.multipliedBy(20));
        when(exponentialBackOffConfig.getRandomFactor()).thenReturn(1.0);
        final ExponentialBackOff backOff = ExponentialBackOff.initial(exponentialBackOffConfig);

        assertThat(backOff.getRestartDelay()).isEqualTo(expected);
    }

    @Test
    public void nextBackOffIsAtLeastTwiceOfMin() {
        final ExponentialBackOffConfig exponentialBackOffConfig = Mockito.mock(ExponentialBackOffConfig.class);
        final Duration min = Duration.ofSeconds(1234);
        when(exponentialBackOffConfig.getMin()).thenReturn(min);
        when(exponentialBackOffConfig.getMax()).thenReturn(min.multipliedBy(20));
        when(exponentialBackOffConfig.getRandomFactor()).thenReturn(1.0);
        final ExponentialBackOff backOff = ExponentialBackOff.initial(exponentialBackOffConfig);

        final ExponentialBackOff nextBackOff = backOff.calculateNextBackOff();

        final Duration expected = min.multipliedBy(2);
        assertThat(nextBackOff.getRestartDelay()).isGreaterThanOrEqualTo(expected);
    }

    @Test
    public void nextBackOffIsCappedToMax() {
        final ExponentialBackOffConfig exponentialBackOffConfig = Mockito.mock(ExponentialBackOffConfig.class);
        final Duration min = Duration.ofSeconds(1234);
        final Duration max = min.plus(Duration.ofSeconds(1));
        when(exponentialBackOffConfig.getMin()).thenReturn(min);
        when(exponentialBackOffConfig.getMax()).thenReturn(max);
        when(exponentialBackOffConfig.getRandomFactor()).thenReturn(1.0);
        final ExponentialBackOff backOff = ExponentialBackOff.initial(exponentialBackOffConfig);

        final ExponentialBackOff nextBackOff = backOff.calculateNextBackOff();

        assertThat(nextBackOff.getRestartDelay()).isEqualTo(max);
    }

    @Test
    public void nextBackOffIsAlsoMinIfWasStableForTwiceOfMax() throws InterruptedException {
        final ExponentialBackOffConfig exponentialBackOffConfig = Mockito.mock(ExponentialBackOffConfig.class);
        final Duration min = Duration.ofSeconds(1);
        final Duration max = Duration.ofSeconds(3);
        when(exponentialBackOffConfig.getMin()).thenReturn(min);
        when(exponentialBackOffConfig.getMax()).thenReturn(max);
        when(exponentialBackOffConfig.getRandomFactor()).thenReturn(1.0);
        final ExponentialBackOff backOff = ExponentialBackOff.initial(exponentialBackOffConfig);

        TimeUnit.SECONDS.sleep(max.multipliedBy(2).toSeconds());

        final ExponentialBackOff nextBackOff = backOff.calculateNextBackOff();

        assertThat(nextBackOff.getRestartDelay()).isEqualTo(min);
    }

}
