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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultWotDirectoryConfig}.
 */
public final class DefaultWotDirectoryConfigTest {

    private static Config wotDirectoryTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        wotDirectoryTestConfig = ConfigFactory.load("wot-directory-test");
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultWotDirectoryConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultWotDirectoryConfig underTest = DefaultWotDirectoryConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getBasePrefix())
                .as(WotDirectoryConfig.ConfigValue.BASE_PREFIX.getConfigPath())
                .isEqualTo("http://localhost:8080");

        softly.assertThat(underTest.getJsonTemplate())
                .as(WotDirectoryConfig.ConfigValue.JSON_TEMPLATE.getConfigPath())
                .isEqualTo(JsonObject.empty());

        softly.assertThat(underTest.isAuthenticationRequired())
                .as(WotDirectoryConfig.ConfigValue.AUTHENTICATION_REQUIRED.getConfigPath())
                .isFalse();
    }

    @Test
    public void underTestReturnsValuesOfBaseConfig() {
        final DefaultWotDirectoryConfig underTest = DefaultWotDirectoryConfig.of(wotDirectoryTestConfig);

        softly.assertThat(underTest.getBasePrefix())
                .as(WotDirectoryConfig.ConfigValue.BASE_PREFIX.getConfigPath())
                .isEqualTo("http://test.example.com");

        softly.assertThat(underTest.getJsonTemplate())
                .as(WotDirectoryConfig.ConfigValue.JSON_TEMPLATE.getConfigPath())
                .isEqualTo(JsonFactory.newObjectBuilder().set("customKey", "customValue").build());

        softly.assertThat(underTest.isAuthenticationRequired())
                .as(WotDirectoryConfig.ConfigValue.AUTHENTICATION_REQUIRED.getConfigPath())
                .isTrue();
    }

    @Test
    public void toStringContainsExpectedInformation() {
        final DefaultWotDirectoryConfig underTest = DefaultWotDirectoryConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.toString())
                .contains("basePath")
                .contains("jsonTemplate")
                .contains("authenticationRequired");
    }

}
