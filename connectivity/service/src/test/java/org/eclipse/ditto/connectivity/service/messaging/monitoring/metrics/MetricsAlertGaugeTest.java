/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.config.Amqp10Config;
import org.eclipse.ditto.connectivity.service.config.Amqp10ConsumerConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectionThrottlingConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.junit.Before;
import org.junit.Test;

public class MetricsAlertGaugeTest {

    private Gauge sut;

    @Before
    public void setup() {
        final var connectivityConf = mock(ConnectivityConfig.class);
        final var connectionConf = mock(ConnectionConfig.class);
        final var amqp10Conf = mock(Amqp10Config.class);
        final var consumerConf = mock(Amqp10ConsumerConfig.class);
        final var throttlingConf = mock(ConnectionThrottlingConfig.class);
        when(connectivityConf.getConnectionConfig()).thenReturn(connectionConf);
        when(connectionConf.getAmqp10Config()).thenReturn(amqp10Conf);
        when(amqp10Conf.getConsumerConfig()).thenReturn(consumerConf);
        when(consumerConf.getThrottlingConfig()).thenReturn(throttlingConf);
        when(throttlingConf.getMaxInFlight()).thenReturn(2);
        sut = MetricAlertRegistry.getMetricsAlertGaugeOrDefault(
                CounterKey.of(ConnectionId.generateRandom(), ""), MetricAlertRegistry.COUNTER_ACK_HANDLING,
                ConnectionType.AMQP_10, connectivityConf);
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
        assertThat(sut.get()).isZero();
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
