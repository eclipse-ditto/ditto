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
package org.eclipse.ditto.base.service.config.supervision;

import java.time.Duration;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultSupervisorConfig}.
 */
public final class DefaultSupervisorConfigTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultSupervisorConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void shutdownTimeoutDefaultsTo12Seconds() {
        final DefaultSupervisorConfig underTest = DefaultSupervisorConfig.of(ConfigFactory.empty());

        Assertions.assertThat(underTest.getShutdownTimeout()).isEqualTo(Duration.ofSeconds(12));
    }

    @Test
    public void shutdownTimeoutIsReadFromSupervisorConfig() {
        final DefaultSupervisorConfig underTest =
                DefaultSupervisorConfig.of(ConfigFactory.parseString("supervisor.shutdown-timeout = 7s"));

        Assertions.assertThat(underTest.getShutdownTimeout()).isEqualTo(Duration.ofSeconds(7));
    }

}
