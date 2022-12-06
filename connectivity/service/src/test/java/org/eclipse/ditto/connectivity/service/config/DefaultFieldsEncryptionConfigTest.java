/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

public class DefaultFieldsEncryptionConfigTest {

    private static Config config;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        config = ConfigFactory.load("connection-fields-encryption-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultFieldsEncryptionConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultFieldsEncryptionConfig.class)
                .usingGetClass()
                .verify();
    }
    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final FieldsEncryptionConfig underTest = DefaultFieldsEncryptionConfig.of(config.getConfig("connection"));

        softly.assertThat(underTest.isEncryptionEnabled()).isTrue();
        softly.assertThat(underTest.getJsonPointers()).containsAll(List.of(
                "/uri",
                "/credentials/key",
                "/sshTunnel/credentials/password",
                "/sshTunnel/credentials/privateKey",
                "/credentials/parameters/accessKey",
                "/credentials/parameters/secretKey",
                "/credentials/parameters/sharedKey",
                "/credentials/clientSecret"));

        softly.assertThat(underTest.getSymmetricalKey()).isEqualTo("vJFSTPE9PO2BtZlcMAwNjs8jdFvQCk0Ya9MVdYjRJUU=");

    }

    @Test(expected = DittoConfigError.class)
    public void missingSymmetricalKeyShouldThrow() {
        DefaultFieldsEncryptionConfig.of(config.getConfig("wrong-connection-config"));
    }
}