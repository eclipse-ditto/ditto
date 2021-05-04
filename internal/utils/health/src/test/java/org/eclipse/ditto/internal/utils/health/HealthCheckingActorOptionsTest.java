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
package org.eclipse.ditto.internal.utils.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.util.function.Supplier;

import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link HealthCheckingActorOptions}.
 */
public final class HealthCheckingActorOptionsTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(HealthCheckingActorOptions.class, areImmutable(),
                provided(Runnable.class, Supplier.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(HealthCheckingActorOptions.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void builderWorksAsExpected() {
        final boolean enabled = true;
        final Duration interval = Duration.ofSeconds(5);

        final HealthCheckingActorOptions healthCheckingActorOptions =
                HealthCheckingActorOptions.getBuilder(enabled, interval)
                        .enablePersistenceCheck()
                        .build();

        assertThat(healthCheckingActorOptions.isHealthCheckEnabled()).isTrue();
        assertThat(healthCheckingActorOptions.isPersistenceCheckEnabled()).isTrue();
        assertThat(healthCheckingActorOptions.getInterval()).isEqualTo(interval);
    }

}
