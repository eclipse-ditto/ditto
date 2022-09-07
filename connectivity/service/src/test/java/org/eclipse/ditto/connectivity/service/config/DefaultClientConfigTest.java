/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultClientConfig}.
 */
public final class DefaultClientConfigTest {

    private static Config clientTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        clientTestConf = ConfigFactory.load("client-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultClientConfig.class,
                areImmutable(),
                provided(ClientConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultClientConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultClientConfig underTest = DefaultClientConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getConnectingMinTimeout())
                .as(ClientConfig.ClientConfigValue.CONNECTING_MIN_TIMEOUT.getConfigPath())
                .isEqualTo(ClientConfig.ClientConfigValue.CONNECTING_MIN_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getConnectingMaxTimeout())
                .as(ClientConfig.ClientConfigValue.CONNECTING_MAX_TIMEOUT.getConfigPath())
                .isEqualTo(ClientConfig.ClientConfigValue.CONNECTING_MAX_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getDisconnectingMaxTimeout())
                .as(ClientConfig.ClientConfigValue.DISCONNECTING_MAX_TIMEOUT.getConfigPath())
                .isEqualTo(ClientConfig.ClientConfigValue.DISCONNECTING_MAX_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getDisconnectAnnouncementTimeout())
                .as(ClientConfig.ClientConfigValue.DISCONNECT_ANNOUNCEMENT_TIMEOUT.getConfigPath())
                .isEqualTo(ClientConfig.ClientConfigValue.DISCONNECT_ANNOUNCEMENT_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getConnectingMaxTries())
                .as(ClientConfig.ClientConfigValue.CONNECTING_MAX_TRIES.getConfigPath())
                .isEqualTo(ClientConfig.ClientConfigValue.CONNECTING_MAX_TRIES.getDefaultValue());
        softly.assertThat(underTest.getTestingTimeout())
                .as(ClientConfig.ClientConfigValue.TESTING_TIMEOUT.getConfigPath())
                .isEqualTo(ClientConfig.ClientConfigValue.TESTING_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getMinBackoff())
                .as(ClientConfig.ClientConfigValue.MIN_BACKOFF.getConfigPath())
                .isEqualTo(ClientConfig.ClientConfigValue.MIN_BACKOFF.getDefaultValue());
        softly.assertThat(underTest.getMaxBackoff())
                .as(ClientConfig.ClientConfigValue.MAX_BACKOFF.getConfigPath())
                .isEqualTo(ClientConfig.ClientConfigValue.MAX_BACKOFF.getDefaultValue());
        softly.assertThat(underTest.getClientActorRefsNotificationDelay())
                .as(ClientConfig.ClientConfigValue.CLIENT_ACTOR_REFS_NOTIFICATION_DELAY.getConfigPath())
                .isEqualTo(ClientConfig.ClientConfigValue.CLIENT_ACTOR_REFS_NOTIFICATION_DELAY.getDefaultValue());
        softly.assertThat(underTest.getSubscriptionRefreshDelay())
                .as(ClientConfig.ClientConfigValue.SUBSCRIPTION_REFRESH_DELAY.getConfigPath())
                .isEqualTo(ClientConfig.ClientConfigValue.SUBSCRIPTION_REFRESH_DELAY.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultClientConfig underTest = DefaultClientConfig.of(clientTestConf);

        softly.assertThat(underTest.getConnectingMinTimeout())
                .as(ClientConfig.ClientConfigValue.CONNECTING_MIN_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(30L));
        softly.assertThat(underTest.getConnectingMaxTimeout())
                .as(ClientConfig.ClientConfigValue.CONNECTING_MAX_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(120L));
        softly.assertThat(underTest.getDisconnectingMaxTimeout())
                .as(ClientConfig.ClientConfigValue.DISCONNECTING_MAX_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(21L));
        softly.assertThat(underTest.getDisconnectAnnouncementTimeout())
                .as(ClientConfig.ClientConfigValue.DISCONNECT_ANNOUNCEMENT_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(11L));
        softly.assertThat(underTest.getConnectingMaxTries())
                .as(ClientConfig.ClientConfigValue.CONNECTING_MAX_TRIES.getConfigPath())
                .isEqualTo(10);
        softly.assertThat(underTest.getTestingTimeout())
                .as(ClientConfig.ClientConfigValue.TESTING_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(5L));
        softly.assertThat(underTest.getMinBackoff())
                .as(ClientConfig.ClientConfigValue.MIN_BACKOFF.getConfigPath())
                .isEqualTo(Duration.ofSeconds(6L));
        softly.assertThat(underTest.getMaxBackoff())
                .as(ClientConfig.ClientConfigValue.MAX_BACKOFF.getConfigPath())
                .isEqualTo(Duration.ofSeconds(7L));
        softly.assertThat(underTest.getClientActorRefsNotificationDelay())
                .as(ClientConfig.ClientConfigValue.CLIENT_ACTOR_REFS_NOTIFICATION_DELAY.getConfigPath())
                .isEqualTo(Duration.ofMinutes(8L));
        softly.assertThat(underTest.getSubscriptionRefreshDelay())
                .as(ClientConfig.ClientConfigValue.SUBSCRIPTION_REFRESH_DELAY.getConfigPath())
                .isEqualTo(Duration.ofMinutes(9L));
    }

}
