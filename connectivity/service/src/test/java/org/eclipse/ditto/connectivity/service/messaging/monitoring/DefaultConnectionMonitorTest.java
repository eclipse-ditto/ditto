/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.ConnectionMetricsCounter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultConnectionMonitor}.
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultConnectionMonitorTest {

    @Mock
    private ConnectionLogger logger;
    @Mock
    private ConnectionMetricsCounter counter;

    @Test
    public void getLogger() {
        final ConnectionMonitor monitor = newMonitor();
        Assertions.assertThat(monitor.getLogger()).isEqualTo(logger);
    }

    @Test
    public void getCounter() {
        final ConnectionMonitor monitor = newMonitor();
        Assertions.assertThat(monitor.getCounter()).isEqualTo(counter);
    }

    @Test
    public void builder() {
        assertThat(DefaultConnectionMonitor.builder(counter, logger)).isNotNull();
    }

    private ConnectionMonitor newMonitor() {
        return DefaultConnectionMonitor.builder(counter, logger).build();
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(DefaultConnectionMonitor.class).verify();
    }

}
