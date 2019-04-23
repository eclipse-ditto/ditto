/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */package org.eclipse.ditto.services.concierge.starter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.time.Duration;

import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.concierge.starter.config.DefaultThingsAggregatorConfig}.
 */
public final class DefaultThingsAggregatorConfigTest {

    private static Config thingsAggregatorTestConf;

    @BeforeClass
    public static void initTestFixture() {
        thingsAggregatorTestConf = ConfigFactory.load("things-aggregator-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultThingsAggregatorConfig.class,
                areImmutable(),
                provided(ThingsAggregatorConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultThingsAggregatorConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultThingsAggregatorConfig underTest = DefaultThingsAggregatorConfig.of(thingsAggregatorTestConf);

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutput objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(underTest);
        objectOutputStream.close();

        final byte[] underTestSerialized = byteArrayOutputStream.toByteArray();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(underTestSerialized);
        final ObjectInput objectInputStream = new ObjectInputStream(byteArrayInputStream);
        final Object underTestDeserialized = objectInputStream.readObject();

        assertThat(underTestDeserialized).isEqualTo(underTest);
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultThingsAggregatorConfig underTest = DefaultThingsAggregatorConfig.of(ConfigFactory.empty());

        assertThat(underTest.getSingleRetrieveThingTimeout())
                .as("getSingleRetrieveThingTimeout")
                .isEqualTo(ThingsAggregatorConfig.ThingsAggregatorConfigValue.SINGLE_RETRIEVE_THING_TIMEOUT.getDefaultValue());

        assertThat(underTest.getMaxParallelism())
                .as("getMaxParallelism")
                .isEqualTo(ThingsAggregatorConfig.ThingsAggregatorConfigValue.MAX_PARALLELISM.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultThingsAggregatorConfig underTest = DefaultThingsAggregatorConfig.of(thingsAggregatorTestConf);

        assertThat(underTest.getSingleRetrieveThingTimeout())
                .as("getSingleRetrieveThingTimeout")
                .isEqualTo(Duration.ofSeconds(60L));

        assertThat(underTest.getMaxParallelism())
                .as("getMaxParallelism")
                .isEqualTo(10);

    }

}
