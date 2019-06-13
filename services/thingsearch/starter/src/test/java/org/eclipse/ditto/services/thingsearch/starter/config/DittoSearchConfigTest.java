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
package org.eclipse.ditto.services.thingsearch.starter.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.services.base.config.DittoServiceConfig;
import org.eclipse.ditto.services.thingsearch.common.config.DittoSearchConfig;
import org.eclipse.ditto.services.utils.health.config.DefaultHealthCheckConfig;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DittoSearchConfig}.
 */
public final class DittoSearchConfigTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoSearchConfig.class,
                areImmutable(),
                provided(DefaultHealthCheckConfig.class, DittoServiceConfig.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DittoSearchConfig.class)
                .usingGetClass()
                .verify();
    }

}