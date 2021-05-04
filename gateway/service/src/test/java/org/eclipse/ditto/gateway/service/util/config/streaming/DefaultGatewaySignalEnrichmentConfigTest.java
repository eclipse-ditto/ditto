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
package org.eclipse.ditto.gateway.service.util.config.streaming;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link DefaultGatewaySignalEnrichmentConfig}
 */
public final class DefaultGatewaySignalEnrichmentConfigTest {

    private static Config signalEnrichmentTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        signalEnrichmentTestConfig = ConfigFactory.load("signal-enrichment-test");
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultGatewaySignalEnrichmentConfig.class, areImmutable(),
                provided(CacheConfig.class, Duration.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultGatewaySignalEnrichmentConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final GatewaySignalEnrichmentConfig underTest = DefaultGatewaySignalEnrichmentConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getAskTimeout())
                .as(GatewaySignalEnrichmentConfig.CachingSignalEnrichmentFacadeConfigValue.ASK_TIMEOUT.getConfigPath())
                .isEqualTo(
                        GatewaySignalEnrichmentConfig.CachingSignalEnrichmentFacadeConfigValue.ASK_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.isCachingEnabled())
                .as(GatewaySignalEnrichmentConfig.CachingSignalEnrichmentFacadeConfigValue.CACHING_ENABLED.getConfigPath())
                .isEqualTo(
                        GatewaySignalEnrichmentConfig.CachingSignalEnrichmentFacadeConfigValue.CACHING_ENABLED.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final GatewaySignalEnrichmentConfig underTest =
                DefaultGatewaySignalEnrichmentConfig.of(signalEnrichmentTestConfig);

        softly.assertThat(underTest.getAskTimeout())
                .as(GatewaySignalEnrichmentConfig.CachingSignalEnrichmentFacadeConfigValue.ASK_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(20));
        softly.assertThat(underTest.isCachingEnabled())
                .as(GatewaySignalEnrichmentConfig.CachingSignalEnrichmentFacadeConfigValue.CACHING_ENABLED.getConfigPath())
                .isEqualTo(false);
    }

}
