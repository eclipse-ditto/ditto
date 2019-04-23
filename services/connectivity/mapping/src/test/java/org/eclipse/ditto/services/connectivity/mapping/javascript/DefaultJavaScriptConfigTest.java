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
package org.eclipse.ditto.services.connectivity.mapping.javascript;

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
 * Unit test for {@link org.eclipse.ditto.services.connectivity.mapping.javascript.DefaultJavaScriptConfig}.
 */
public final class DefaultJavaScriptConfigTest {

    private static Config javascriptTestConf;

    @BeforeClass
    public static void initTestFixture() {
        javascriptTestConf = ConfigFactory.load("javascript-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultJavaScriptConfig.class,
                areImmutable(),
                provided(JavaScriptConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultJavaScriptConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultJavaScriptConfig underTest = DefaultJavaScriptConfig.of(javascriptTestConf);

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
        final DefaultJavaScriptConfig underTest = DefaultJavaScriptConfig.of(ConfigFactory.empty());

        assertThat(underTest.getMaxScriptSizeBytes())
                .as("getMaxScriptSizeBytes")
                .isEqualTo(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_SIZE_BYTES.getDefaultValue());

        assertThat(underTest.getMaxScriptExecutionTime())
                .as("getMaxScriptExecutionTime")
                .isEqualTo(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_EXECUTION_TIME.getDefaultValue());

        assertThat(underTest.getMaxScriptStackDepth())
                .as("getMaxScriptStackDepth")
                .isEqualTo(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_STACK_DEPTH.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultJavaScriptConfig underTest = DefaultJavaScriptConfig.of(javascriptTestConf);

        assertThat(underTest.getMaxScriptSizeBytes())
                .as("getMaxScriptSizeBytes")
                .isEqualTo(10000);

        assertThat(underTest.getMaxScriptExecutionTime())
                .as("getMaxScriptExecutionTime")
                .isEqualTo(Duration.ofMillis(100L));

        assertThat(underTest.getMaxScriptStackDepth())
                .as("getMaxScriptStackDepth")
                .isEqualTo(1);
    }
}
