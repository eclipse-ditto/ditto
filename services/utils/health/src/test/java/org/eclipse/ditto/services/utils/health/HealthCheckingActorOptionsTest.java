/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.health;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.util.function.Supplier;

import org.junit.Assert;
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
                .withPrefabValues(Supplier.class, () -> null, () -> null)
                .usingGetClass()
                .verify();
    }


    @Test
    public void builderWorksAsExpected() {
        final boolean enabled = true;
        final Duration interval = Duration.ofSeconds(5);

        final HealthCheckingActorOptions healthCheckingActorOptions =
                HealthCheckingActorOptions.getBuilder(enabled, interval) //
                        .enablePersistenceCheck() //
                        .build();

        Assert.assertTrue(healthCheckingActorOptions.isHealthCheckEnabled());
        Assert.assertTrue(healthCheckingActorOptions.isPersistenceCheckEnabled());

        Assert.assertEquals(healthCheckingActorOptions.getInterval(), interval);
    }
}
