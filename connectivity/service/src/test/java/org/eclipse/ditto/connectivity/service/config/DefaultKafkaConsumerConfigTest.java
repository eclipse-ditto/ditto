/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import com.typesafe.config.Config;

import nl.jqno.equalsverifier.EqualsVerifier;

public class DefaultKafkaConsumerConfigTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultKafkaConsumerConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultKafkaConsumerConfig.class, areImmutable(), provided(Config.class,
                ConnectionThrottlingConfig.class).areAlsoImmutable());
    }

}
