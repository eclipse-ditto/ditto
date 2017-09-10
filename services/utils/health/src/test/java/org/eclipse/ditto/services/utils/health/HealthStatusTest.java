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

import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Assert;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link HealthStatus}.
 */
public final class HealthStatusTest {


    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(HealthStatus.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(HealthStatus.class).usingGetClass().verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullStatus() {
        HealthStatus.of(null, null);
    }


    @Test
    public void jsonSerializationWorksAsExpected() {
        final HealthStatus healthStatusWithOnlyStatus = HealthStatus.of(HealthStatus.Status.UP, null);
        Assert.assertTrue(
                healthStatusWithOnlyStatus.toJson().asObject().contains(HealthStatus.JSON_KEY_STATUS.getPointer()));
        Assert.assertFalse(
                healthStatusWithOnlyStatus.toJson().asObject().contains(HealthStatus.JSON_KEY_DETAIL.getPointer()));

        final HealthStatus healthStatusWithBoth = HealthStatus.of(HealthStatus.Status.UP, "some detail information");
        Assert.assertTrue(healthStatusWithBoth.toJson().asObject().contains(HealthStatus.JSON_KEY_STATUS.getPointer()));
        Assert.assertTrue(healthStatusWithBoth.toJson().asObject().contains(HealthStatus.JSON_KEY_DETAIL.getPointer()));
    }
}
