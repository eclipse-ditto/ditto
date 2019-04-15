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

package org.eclipse.ditto.services.connectivity.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.junit.Test;

import com.typesafe.config.Config;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.util.MonitoringConfigReader}.
 */
public final class MonitoringConfigReaderTest {

    private static final Config CONFIG = ConfigUtil.determineConfig("test.conf");

    private final MonitoringConfigReader underTest = ConfigKeys.Monitoring.fromRawConfig(CONFIG);

    @Test
    public void loggerNotNull() {
        assertThat(underTest.logger()).isNotNull();
    }

    @Test
    public void logDuration() {
        assertThat(underTest.logger().logDuration()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    public void successCapacity() {
        assertThat(underTest.logger().successCapacity()).isEqualTo(1);
    }

    @Test
    public void failureCapacity() {
        assertThat(underTest.logger().failureCapacity()).isEqualTo(3);
    }

    @Test
    public void counterNotNull() {
        assertThat(underTest.counter()).isNotNull();
    }

}
