/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.reflect.Constructor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

/**
 * Unit test for {@link InstanceIdentifierSupplier}.
 */
public final class InstanceIdentifierSupplierTest {

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void setUp() {
        environmentVariables.clear(InstanceIdentifierSupplier.ENV_INSTANCE_INDEX);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(InstanceIdentifierSupplier.class,
                areImmutable(),
                assumingFields("instanceIdentifier").areModifiedAsPartOfAnUnobservableCachingStrategy());
    }

    @Test
    public void getInstanceReturnsSingleton() {
        final InstanceIdentifierSupplier firstObtainedInstance = InstanceIdentifierSupplier.getInstance();
        final InstanceIdentifierSupplier secondObtainedInstance = InstanceIdentifierSupplier.getInstance();

        assertThat(firstObtainedInstance).isSameAs(secondObtainedInstance);
    }

    @Test
    public void instanceIndexIsObtainedFromEnvironmentVariable() {
        final String instanceIndex = "foo-service:3";
        environmentVariables.set(InstanceIdentifierSupplier.ENV_INSTANCE_INDEX, instanceIndex);

        final InstanceIdentifierSupplier underTest = InstanceIdentifierSupplier.getInstance();

        assertThat(underTest.get()).isEqualTo(instanceIndex);
    }

    @Test
    public void instanceIndexIsObtainedFromHostNameSupplier() {
        final HostNameSupplier hostNameSupplier = HostNameSupplier.getInstance();
        final String instanceIndex = hostNameSupplier.get();

        final InstanceIdentifierSupplier underTest = tryToGetInstancePerReflection();

        assertThat(underTest.get()).isEqualTo(instanceIndex);
    }

    private static InstanceIdentifierSupplier tryToGetInstancePerReflection() {
        try {
            return getInstancePerReflection();
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to create object of InstanceIdentifierSupplier per reflection!", e);
        }
    }

    /**
     * Returns an object of {@link InstanceIdentifierSupplier} which was created by reflection to circumvent a
     * possibly cached instance index.
     */
    private static InstanceIdentifierSupplier getInstancePerReflection() throws Exception {
        final Constructor<InstanceIdentifierSupplier> ctor = InstanceIdentifierSupplier.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

}