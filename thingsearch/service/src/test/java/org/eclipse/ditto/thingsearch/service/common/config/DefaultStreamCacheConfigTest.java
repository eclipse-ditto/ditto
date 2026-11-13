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
package org.eclipse.ditto.thingsearch.service.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.thingsearch.service.common.config.StreamCacheConfig.StreamCacheConfigValue;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link DefaultStreamCacheConfig}.
 */
public final class DefaultStreamCacheConfigTest {

    private static final String THING_CACHE = "thing-cache";
    private static final String POLICY_CACHE = "policy-cache";

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultStreamCacheConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final StreamCacheConfig underTest = DefaultStreamCacheConfig.of(ConfigFactory.empty(), THING_CACHE);

        assertThat(underTest.getRetryDelay())
                .as(StreamCacheConfigValue.RETRY_DELAY.getConfigPath())
                .isEqualTo(StreamCacheConfigValue.RETRY_DELAY.getDefaultValue());
        assertThat(underTest.getDispatcherName())
                .as(StreamCacheConfigValue.DISPATCHER_NAME.getConfigPath())
                .isEqualTo(StreamCacheConfigValue.DISPATCHER_NAME.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfig() {
        final Config config = ConfigFactory.parseString(
                "thing-cache {\n"
                        + "  dispatcher = \"thing-cache-dispatcher\"\n"
                        + "  retry-delay = 3s\n"
                        + "  maximum-size = 7\n"
                        + "}\n"
                        + "policy-cache {\n"
                        + "  retry-delay = 2s\n"
                        + "}\n");

        final StreamCacheConfig thingCache = DefaultStreamCacheConfig.of(config, THING_CACHE);
        final StreamCacheConfig policyCache = DefaultStreamCacheConfig.of(config, POLICY_CACHE);

        assertThat(thingCache.getRetryDelay()).isEqualTo(Duration.ofSeconds(3L));
        assertThat(thingCache.getDispatcherName()).isEqualTo("thing-cache-dispatcher");
        assertThat(thingCache.getMaximumSize()).isEqualTo(7L);
        assertThat(policyCache.getRetryDelay()).isEqualTo(Duration.ofSeconds(2L));
    }

    @Test
    public void negativeRetryDelayIsRejected() {
        final Config config = ConfigFactory.parseString("thing-cache.retry-delay = -1s\n");

        assertThatThrownBy(() -> DefaultStreamCacheConfig.of(config, THING_CACHE))
                .isInstanceOf(DittoConfigError.class);
    }

    /**
     * Regression pin: search.conf's {@code thing-cache} block used to carry no
     * {@code ${?THINGS_SEARCH_UPDATER_STREAM_THING_CACHE_RETRY_DELAY}} binding, so the env var the system-test
     * workflow exports was silently discarded. Resolving the REAL search.conf against a fake environment proves the
     * binding exists (the policy-cache binding is the pre-existing counterpart and must stay untouched).
     */
    @Test
    public void thingCacheRetryDelayEnvOverrideIsBoundInSearchConf() {
        final Config fakeEnvironment = ConfigFactory.parseMap(
                Map.of("THINGS_SEARCH_UPDATER_STREAM_THING_CACHE_RETRY_DELAY", "42s"));
        final Config searchConf = fakeEnvironment
                .withFallback(ConfigFactory.parseResources("search.conf"))
                .resolve(ConfigResolveOptions.defaults().setUseSystemEnvironment(false).setAllowUnresolved(true));

        assertThat(searchConf.getDuration("ditto.search.updater.stream.thing-cache.retry-delay"))
                .isEqualTo(Duration.ofSeconds(42L));
        assertThat(searchConf.getDuration("ditto.search.updater.stream.policy-cache.retry-delay"))
                .isEqualTo(Duration.ofSeconds(1L));
    }

}
