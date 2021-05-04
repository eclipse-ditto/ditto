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
package org.eclipse.ditto.internal.utils.config;

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
 * Unit test for {@link LocalHostAddressSupplier}.
 */
public final class LocalHostAddressSupplierTest {

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void setUp() {
        environmentVariables.clear(HostNameSupplier.ENV_HOSTNAME);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(LocalHostAddressSupplier.class,
                areImmutable(),
                assumingFields("localHostAddress").areModifiedAsPartOfAnUnobservableCachingStrategy());
    }

    @Test
    public void getInstanceReturnsSingleton() {
        final LocalHostAddressSupplier firstObtainedInstance = LocalHostAddressSupplier.getInstance();
        final LocalHostAddressSupplier secondObtainedInstance = LocalHostAddressSupplier.getInstance();

        assertThat(firstObtainedInstance).isSameAs(secondObtainedInstance);
    }

    @Test
    public void localHostAddressIsObtainedFromEnvironmentVariable() {
        final String localHostAddress = "10.20.20.40";
        environmentVariables.set(HostNameSupplier.ENV_HOSTNAME, localHostAddress);

        final LocalHostAddressSupplier underTest = LocalHostAddressSupplier.getInstance();

        assertThat(underTest.get()).isEqualTo(localHostAddress);
    }

    @Test
    public void localHostAddressIsObtainedFromHostNameSupplier() {
        final HostNameSupplier hostNameSupplier = HostNameSupplier.getInstance();
        final String hostName = hostNameSupplier.get();

        final LocalHostAddressSupplier underTest = tryToGetInstancePerReflection();

        assertThat(underTest.get()).isEqualTo(hostName);
    }

    private static LocalHostAddressSupplier tryToGetInstancePerReflection() {
        try {
            return getInstancePerReflection();
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to create object of LocalHostAddressSupplier per reflection!", e);
        }
    }

    /**
     * Returns an object of {@link LocalHostAddressSupplier} which was created by reflection to circumvent a
     * possibly cached instance index.
     */
    private static LocalHostAddressSupplier getInstancePerReflection() throws Exception {
        final Constructor<LocalHostAddressSupplier> constructor =
                LocalHostAddressSupplier.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

}
