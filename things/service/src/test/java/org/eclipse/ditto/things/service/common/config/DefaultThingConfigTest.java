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
package org.eclipse.ditto.things.service.common.config;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for the supervisor settings of {@link DefaultThingConfig}.
 */
public final class DefaultThingConfigTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void supervisorShutdownTimeoutIsReadFromThingSupervisorConfig() {
        final DefaultThingConfig underTest =
                DefaultThingConfig.of(ConfigFactory.parseString("thing.supervisor.shutdown-timeout = 9s"));

        softly.assertThat(underTest.getSupervisorConfig().getShutdownTimeout()).isEqualTo(Duration.ofSeconds(9L));
    }

    /**
     * {@code THING_SHUTDOWN_TIMEOUT} configured {@code thing.shutdown-timeout} before; it now configures the
     * supervisor's {@code shutdown-timeout}, so existing deployments keep their override.
     */
    @Test
    public void shippedConfigDefinesSupervisorShutdownTimeoutWithEnvironmentOverride() {
        final Config shipped = ConfigFactory.parseResources("things.conf");

        final DefaultThingConfig defaults = DefaultThingConfig.of(shipped.resolve().getConfig("ditto.things"));
        softly.assertThat(defaults.getSupervisorConfig().getShutdownTimeout())
                .as("default supervisor shutdown-timeout")
                .isEqualTo(Duration.ofSeconds(12L));

        final Config environmentOverride = ConfigFactory.parseString("THING_SHUTDOWN_TIMEOUT = 7s");
        final DefaultThingConfig overridden =
                DefaultThingConfig.of(environmentOverride.withFallback(shipped).resolve().getConfig("ditto.things"));
        softly.assertThat(overridden.getSupervisorConfig().getShutdownTimeout())
                .as("THING_SHUTDOWN_TIMEOUT")
                .isEqualTo(Duration.ofSeconds(7L));
    }

}
