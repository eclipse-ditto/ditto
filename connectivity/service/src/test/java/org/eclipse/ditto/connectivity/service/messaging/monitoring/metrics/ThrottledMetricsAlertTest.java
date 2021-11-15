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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.MeasurementWindow.ONE_MINUTE_WITH_ONE_MINUTE_RESOLUTION;
import static org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.MeasurementWindow.ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests ThrottledMetricsAlert.
 */
public class ThrottledMetricsAlertTest {

    private static final long NOW = System.currentTimeMillis();
    private ConnectionMetricsCounter metricsCounter;
    private MetricsAlert underTest;

    @Before
    public void setUp() throws Exception {
        metricsCounter = mock(ConnectionMetricsCounter.class);
        underTest = new ThrottledMetricsAlert(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION, 10,
                () -> metricsCounter);
    }

    @Test
    public void testCondition() {


        assertThat(underTest.evaluateCondition(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION, NOW, 0))
                .isFalse();
        assertThat(underTest.evaluateCondition(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION, NOW, 10))
                .isFalse();
        assertThat(underTest.evaluateCondition(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION, NOW, 11))
                .isTrue();
        assertThat(underTest.evaluateCondition(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION, NOW, 100))
                .isTrue();

        // wrong window
        assertThat(underTest.evaluateCondition(ONE_MINUTE_WITH_ONE_MINUTE_RESOLUTION, NOW, 1000))
                .isFalse();
    }

    @Test
    public void testAction() {
        underTest.triggerAction(NOW, 100);
        verify(metricsCounter).recordFailure(NOW);
    }

    @Test
    public void testLookupReturnsNull() {
        assertThatNoException().isThrownBy(() -> new ThrottledMetricsAlert(ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION, 10,
                () -> metricsCounter).triggerAction(NOW, 100));

    }
}
