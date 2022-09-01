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
package org.eclipse.ditto.gateway.service.util.config.security;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.service.config.http.HttpProxyConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultAuthenticationConfig}.
 */
public final class DefaultAuthenticationConfigTest {

    private static Config authenticationTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        authenticationTestConf = ConfigFactory.load("authentication-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultAuthenticationConfig.class,
                areImmutable(),
                provided(HttpProxyConfig.class, DefaultOAuthConfig.class, DefaultDevOpsConfig.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultAuthenticationConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultAuthenticationConfig underTest = DefaultAuthenticationConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isPreAuthenticationEnabled())
                .as(AuthenticationConfig.AuthenticationConfigValue.PRE_AUTHENTICATION_ENABLED.getConfigPath())
                .isEqualTo(AuthenticationConfig.AuthenticationConfigValue.PRE_AUTHENTICATION_ENABLED.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultAuthenticationConfig underTest = DefaultAuthenticationConfig.of(authenticationTestConf);

        softly.assertThat(underTest.isPreAuthenticationEnabled())
                .as(AuthenticationConfig.AuthenticationConfigValue.PRE_AUTHENTICATION_ENABLED.getConfigPath())
                .isTrue();
        softly.assertThat(underTest.getHttpProxyConfig())
                .as("httpProxyConfig")
                .satisfies(httpProxyConfig -> {
                    softly.assertThat(httpProxyConfig.isEnabled())
                            .as(HttpProxyConfig.HttpProxyConfigValue.ENABLED.getConfigPath())
                            .isTrue();
                    softly.assertThat(httpProxyConfig.getHostname())
                            .as(HttpProxyConfig.HttpProxyConfigValue.HOST_NAME.getConfigPath())
                            .isEqualTo("example.com");
                    softly.assertThat(httpProxyConfig.getPort())
                            .as(HttpProxyConfig.HttpProxyConfigValue.PORT.getConfigPath())
                            .isEqualTo(4711);
                    softly.assertThat(httpProxyConfig.getUsername())
                            .as(HttpProxyConfig.HttpProxyConfigValue.USER_NAME.getConfigPath())
                            .isEqualTo("john.frume");
                    softly.assertThat(httpProxyConfig.getPassword())
                            .as(HttpProxyConfig.HttpProxyConfigValue.USER_NAME.getConfigPath())
                            .isEqualTo("verySecretPW!");
                });
    }

}
