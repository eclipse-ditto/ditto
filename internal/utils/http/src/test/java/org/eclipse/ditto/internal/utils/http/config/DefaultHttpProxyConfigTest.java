/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.http.config;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.config.http.HttpProxyBaseConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultHttpProxyConfig}.
 */
public final class DefaultHttpProxyConfigTest {

    private static Config httpProxyConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        httpProxyConfig = ConfigFactory.load("http-proxy-test");
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultHttpProxyConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultHttpProxyConfig underTest = DefaultHttpProxyConfig.ofHttpProxy(ConfigFactory.empty());

        softly.assertThat(underTest.isEnabled())
                .as(HttpProxyBaseConfig.HttpProxyConfigValue.ENABLED.getConfigPath())
                .isEqualTo(HttpProxyBaseConfig.HttpProxyConfigValue.ENABLED.getDefaultValue());
        softly.assertThat(underTest.getHostname())
                .as(HttpProxyBaseConfig.HttpProxyConfigValue.HOST_NAME.getConfigPath())
                .isEqualTo(HttpProxyBaseConfig.HttpProxyConfigValue.HOST_NAME.getDefaultValue());
        softly.assertThat(underTest.getPort())
                .as(HttpProxyBaseConfig.HttpProxyConfigValue.PORT.getConfigPath())
                .isEqualTo(HttpProxyBaseConfig.HttpProxyConfigValue.PORT.getDefaultValue());
        softly.assertThat(underTest.getUsername())
                .as(HttpProxyBaseConfig.HttpProxyConfigValue.USER_NAME.getConfigPath())
                .isEqualTo(HttpProxyBaseConfig.HttpProxyConfigValue.USER_NAME.getDefaultValue());
        softly.assertThat(underTest.getPassword())
                .as(HttpProxyBaseConfig.HttpProxyConfigValue.PASSWORD.getConfigPath())
                .isEqualTo(HttpProxyBaseConfig.HttpProxyConfigValue.PASSWORD.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultHttpProxyConfig underTest = DefaultHttpProxyConfig.ofHttpProxy(httpProxyConfig);

        softly.assertThat(underTest.isEnabled())
                .as(HttpProxyBaseConfig.HttpProxyConfigValue.ENABLED.getConfigPath())
                .isTrue();
        softly.assertThat(underTest.getHostname())
                .as(HttpProxyBaseConfig.HttpProxyConfigValue.HOST_NAME.getConfigPath())
                .isEqualTo("example.com");
        softly.assertThat(underTest.getPort())
                .as(HttpProxyBaseConfig.HttpProxyConfigValue.PORT.getConfigPath())
                .isEqualTo(4711);
        softly.assertThat(underTest.getUsername())
                .as(HttpProxyBaseConfig.HttpProxyConfigValue.USER_NAME.getConfigPath())
                .isEqualTo("john.frume");
        softly.assertThat(underTest.getPassword())
                .as(HttpProxyBaseConfig.HttpProxyConfigValue.PASSWORD.getConfigPath())
                .isEqualTo("verySecretPW!");
    }

}
