/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit tests for {@link HealthStatus}.
 */
public final class HealthStatusTest {

    @Test
    public void allExpectedValuesPresent() {
        assertThat(HealthStatus.values())
                .containsExactly(HealthStatus.UP, HealthStatus.DEGRADED, HealthStatus.DOWN);
    }

    @Test
    public void valueOfMatchesByName() {
        assertThat(HealthStatus.valueOf("UP")).isEqualTo(HealthStatus.UP);
        assertThat(HealthStatus.valueOf("DEGRADED")).isEqualTo(HealthStatus.DEGRADED);
        assertThat(HealthStatus.valueOf("DOWN")).isEqualTo(HealthStatus.DOWN);
    }
}
