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
package org.eclipse.ditto.internal.utils.pekko.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link DynamicConfigWatcherExtension}.
 */
public final class DynamicConfigWatcherExtensionTest {

    private ActorSystem system;

    @Before
    public void setup() {
        system = ActorSystem.create("DynamicConfigWatcherExtensionTest",
                ConfigFactory.parseString("ditto.things.thing.activity-check.inactive-interval = 2h"));
    }

    @After
    public void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void getDittoConfigReturnsStaticConfigWhenDisabled() {
        final DynamicConfigWatcherExtension extension = DynamicConfigWatcherExtension.get(system);

        assertThat(extension.isEnabled()).isFalse();
        assertThat(extension.getDittoConfig().hasPath("ditto")).isTrue();
        assertThat(extension.getDittoConfig().hasPath("ditto.things")).isTrue();
    }

    @Test
    public void getVersionStartsAtZero() {
        final DynamicConfigWatcherExtension extension = DynamicConfigWatcherExtension.get(system);

        assertThat(extension.getVersion()).isEqualTo(0L);
    }

    @Test
    public void extensionIsSingleton() {
        final DynamicConfigWatcherExtension extension1 = DynamicConfigWatcherExtension.get(system);
        final DynamicConfigWatcherExtension extension2 = DynamicConfigWatcherExtension.get(system);

        assertThat(extension1).isSameAs(extension2);
    }

    @Test
    public void updateConfigIncrementsVersion() {
        final DynamicConfigWatcherExtension extension = DynamicConfigWatcherExtension.get(system);

        final var newConfig = ConfigFactory.parseString("ditto.things.thing.activity-check.inactive-interval = 5m")
                .withFallback(system.settings().config());
        final var dittoConfig = newConfig.getConfig("ditto").atKey("ditto");

        final long newVersion = extension.updateConfig(dittoConfig);
        assertThat(newVersion).isEqualTo(1L);
        assertThat(extension.getVersion()).isEqualTo(1L);
        assertThat(extension.getDittoConfig()).isEqualTo(dittoConfig);
    }

    @Test
    public void getParsedConfigReturnsCachedValueForSameVersion() {
        final DynamicConfigWatcherExtension extension = DynamicConfigWatcherExtension.get(system);
        final AtomicInteger parseCount = new AtomicInteger(0);

        final String result1 = extension.getParsedConfig("testKey", config -> {
            parseCount.incrementAndGet();
            return "parsed-" + extension.getVersion();
        });
        final String result2 = extension.getParsedConfig("testKey", config -> {
            parseCount.incrementAndGet();
            return "parsed-" + extension.getVersion();
        });

        assertThat(result1).isEqualTo("parsed-0");
        assertThat(result2).isSameAs(result1);
        assertThat(parseCount.get()).isEqualTo(1);
    }

    @Test
    public void getParsedConfigInvalidatesCacheOnVersionChange() {
        final DynamicConfigWatcherExtension extension = DynamicConfigWatcherExtension.get(system);
        final AtomicInteger parseCount = new AtomicInteger(0);

        final String result1 = extension.getParsedConfig("testKey", config -> {
            parseCount.incrementAndGet();
            return "parsed-" + extension.getVersion();
        });
        assertThat(result1).isEqualTo("parsed-0");

        // Trigger version change
        final var newConfig = ConfigFactory.parseString("ditto.things.thing.activity-check.inactive-interval = 5m")
                .withFallback(system.settings().config());
        extension.updateConfig(newConfig.getConfig("ditto").atKey("ditto"));

        final String result2 = extension.getParsedConfig("testKey", config -> {
            parseCount.incrementAndGet();
            return "parsed-" + extension.getVersion();
        });

        assertThat(result2).isEqualTo("parsed-1");
        assertThat(result2).isNotSameAs(result1);
        assertThat(parseCount.get()).isEqualTo(2);
    }

    @Test
    public void getParsedConfigDifferentKeysAreCachedIndependently() {
        final DynamicConfigWatcherExtension extension = DynamicConfigWatcherExtension.get(system);

        final String resultA = extension.getParsedConfig("keyA", config -> "valueA");
        final String resultB = extension.getParsedConfig("keyB", config -> "valueB");

        assertThat(resultA).isEqualTo("valueA");
        assertThat(resultB).isEqualTo("valueB");
    }
}
