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

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.util.Map;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.service.config.http.HttpProxyConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

public final class DefaultHttpPushConfigTest {

    private static Config config;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        config = ConfigFactory.load("http-push-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultHttpPushConfig.class,
                areImmutable(),
                provided(HttpProxyConfig.class, OAuth2Config.class).areAlsoImmutable(),
                assumingFields("hmacAlgorithms", "omitRequestBodyMethods")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultHttpPushConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final HttpPushConfig underTest = DefaultHttpPushConfig.of(config);

        softly.assertThat(underTest.getMaxQueueSize())
                .describedAs(HttpPushConfig.ConfigValue.MAX_QUEUE_SIZE.getConfigPath())
                .isEqualTo(1);

        softly.assertThat(underTest.getRequestTimeout())
                .describedAs(HttpPushConfig.ConfigValue.REQUEST_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(2));

        final HttpProxyConfig proxyConfig = underTest.getHttpProxyConfig();
        softly.assertThat(proxyConfig.isEnabled())
                .describedAs(HttpProxyConfig.HttpProxyConfigValue.ENABLED.getConfigPath())
                .isTrue();

        softly.assertThat(proxyConfig.getHostname())
                .describedAs(HttpProxyConfig.HttpProxyConfigValue.HOST_NAME.getConfigPath())
                .isEqualTo("host3");

        softly.assertThat(proxyConfig.getPort())
                .describedAs(HttpProxyConfig.HttpProxyConfigValue.PORT.getConfigPath())
                .isEqualTo(4);

        softly.assertThat(proxyConfig.getUsername())
                .describedAs(HttpProxyConfig.HttpProxyConfigValue.USER_NAME.getConfigPath())
                .isEqualTo("user5");

        softly.assertThat(proxyConfig.getPassword())
                .describedAs(HttpProxyConfig.HttpProxyConfigValue.PASSWORD.getConfigPath())
                .isEqualTo("pass6");

        softly.assertThat(underTest.getHmacAlgorithms())
                .describedAs(HttpPushConfig.ConfigValue.HMAC_ALGORITHMS.getConfigPath())
                .isEqualTo(Map.of("algorithm1", "factory1", "algorithm2", "factory2"));
    }

}
