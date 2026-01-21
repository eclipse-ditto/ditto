/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultNamespaceActivityCheckConfig}.
 */
public final class DefaultNamespaceActivityCheckConfigTest {

    private static Config namespaceActivityCheckTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        namespaceActivityCheckTestConf = ConfigFactory.load("namespace-activity-check-test");
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultNamespaceActivityCheckConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultNamespaceActivityCheckConfig underTest =
                DefaultNamespaceActivityCheckConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getNamespacePattern())
                .as(NamespaceActivityCheckConfig.NamespaceActivityCheckConfigValue.NAMESPACE_PATTERN.getConfigPath())
                .isEqualTo(NamespaceActivityCheckConfig.NamespaceActivityCheckConfigValue.NAMESPACE_PATTERN.getDefaultValue());
        softly.assertThat(underTest.getInactiveInterval())
                .as(NamespaceActivityCheckConfig.NamespaceActivityCheckConfigValue.INACTIVE_INTERVAL.getConfigPath())
                .isEqualTo(NamespaceActivityCheckConfig.NamespaceActivityCheckConfigValue.INACTIVE_INTERVAL.getDefaultValue());
        softly.assertThat(underTest.getDeletedInterval())
                .as(NamespaceActivityCheckConfig.NamespaceActivityCheckConfigValue.DELETED_INTERVAL.getConfigPath())
                .isEqualTo(NamespaceActivityCheckConfig.NamespaceActivityCheckConfigValue.DELETED_INTERVAL.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultNamespaceActivityCheckConfig underTest =
                DefaultNamespaceActivityCheckConfig.of(namespaceActivityCheckTestConf);

        softly.assertThat(underTest.getNamespacePattern())
                .as(NamespaceActivityCheckConfig.NamespaceActivityCheckConfigValue.NAMESPACE_PATTERN.getConfigPath())
                .isEqualTo("org.example*");
        softly.assertThat(underTest.getInactiveInterval())
                .as(NamespaceActivityCheckConfig.NamespaceActivityCheckConfigValue.INACTIVE_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofMinutes(30L));
        softly.assertThat(underTest.getDeletedInterval())
                .as(NamespaceActivityCheckConfig.NamespaceActivityCheckConfigValue.DELETED_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofMinutes(10L));
    }
}
