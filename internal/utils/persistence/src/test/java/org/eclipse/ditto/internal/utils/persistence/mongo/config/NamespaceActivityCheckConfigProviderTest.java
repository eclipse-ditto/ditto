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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link NamespaceActivityCheckConfigProvider}.
 */
public final class NamespaceActivityCheckConfigProviderTest {

    private static final Duration DEFAULT_INACTIVE_INTERVAL = Duration.ofHours(2L);
    private static final Duration DEFAULT_DELETED_INTERVAL = Duration.ofMinutes(5L);

    private static final Duration CUSTOM_INACTIVE_INTERVAL = Duration.ofMinutes(30L);
    private static final Duration CUSTOM_DELETED_INTERVAL = Duration.ofMinutes(10L);

    @Test
    public void returnsDefaultConfigWhenNoNamespaceConfigsExist() {
        final ActivityCheckConfig defaultConfig = createDefaultConfig();
        final NamespaceActivityCheckConfigProvider underTest =
                NamespaceActivityCheckConfigProvider.of(List.of(), defaultConfig);

        final ActivityCheckConfig result = underTest.getConfigForNamespace("org.eclipse.ditto");

        assertThat(result.getInactiveInterval()).isEqualTo(DEFAULT_INACTIVE_INTERVAL);
        assertThat(result.getDeletedInterval()).isEqualTo(DEFAULT_DELETED_INTERVAL);
    }

    @Test
    public void returnsDefaultConfigWhenNamespaceIsNull() {
        final ActivityCheckConfig defaultConfig = createDefaultConfig();
        final NamespaceActivityCheckConfig customConfig = createCustomConfig("org.example*");
        final NamespaceActivityCheckConfigProvider underTest =
                NamespaceActivityCheckConfigProvider.of(List.of(customConfig), defaultConfig);

        final ActivityCheckConfig result = underTest.getConfigForNamespace(null);

        assertThat(result.getInactiveInterval()).isEqualTo(DEFAULT_INACTIVE_INTERVAL);
        assertThat(result.getDeletedInterval()).isEqualTo(DEFAULT_DELETED_INTERVAL);
    }

    @Test
    public void returnsDefaultConfigWhenNamespaceIsEmpty() {
        final ActivityCheckConfig defaultConfig = createDefaultConfig();
        final NamespaceActivityCheckConfig customConfig = createCustomConfig("org.example*");
        final NamespaceActivityCheckConfigProvider underTest =
                NamespaceActivityCheckConfigProvider.of(List.of(customConfig), defaultConfig);

        final ActivityCheckConfig result = underTest.getConfigForNamespace("");

        assertThat(result.getInactiveInterval()).isEqualTo(DEFAULT_INACTIVE_INTERVAL);
        assertThat(result.getDeletedInterval()).isEqualTo(DEFAULT_DELETED_INTERVAL);
    }

    @Test
    public void returnsDefaultConfigWhenNoPatternMatches() {
        final ActivityCheckConfig defaultConfig = createDefaultConfig();
        final NamespaceActivityCheckConfig customConfig = createCustomConfig("org.example*");
        final NamespaceActivityCheckConfigProvider underTest =
                NamespaceActivityCheckConfigProvider.of(List.of(customConfig), defaultConfig);

        final ActivityCheckConfig result = underTest.getConfigForNamespace("com.other.namespace");

        assertThat(result.getInactiveInterval()).isEqualTo(DEFAULT_INACTIVE_INTERVAL);
        assertThat(result.getDeletedInterval()).isEqualTo(DEFAULT_DELETED_INTERVAL);
    }

    @Test
    public void returnsCustomConfigWhenExactPatternMatches() {
        final ActivityCheckConfig defaultConfig = createDefaultConfig();
        final NamespaceActivityCheckConfig customConfig = createCustomConfig("org.example.myapp");
        final NamespaceActivityCheckConfigProvider underTest =
                NamespaceActivityCheckConfigProvider.of(List.of(customConfig), defaultConfig);

        final ActivityCheckConfig result = underTest.getConfigForNamespace("org.example.myapp");

        assertThat(result.getInactiveInterval()).isEqualTo(CUSTOM_INACTIVE_INTERVAL);
        assertThat(result.getDeletedInterval()).isEqualTo(CUSTOM_DELETED_INTERVAL);
    }

    @Test
    public void returnsCustomConfigWhenWildcardPatternMatches() {
        final ActivityCheckConfig defaultConfig = createDefaultConfig();
        final NamespaceActivityCheckConfig customConfig = createCustomConfig("org.example*");
        final NamespaceActivityCheckConfigProvider underTest =
                NamespaceActivityCheckConfigProvider.of(List.of(customConfig), defaultConfig);

        final ActivityCheckConfig result = underTest.getConfigForNamespace("org.example.myapp.subpackage");

        assertThat(result.getInactiveInterval()).isEqualTo(CUSTOM_INACTIVE_INTERVAL);
        assertThat(result.getDeletedInterval()).isEqualTo(CUSTOM_DELETED_INTERVAL);
    }

    @Test
    public void returnsCustomConfigWhenSingleCharWildcardMatches() {
        final ActivityCheckConfig defaultConfig = createDefaultConfig();
        final NamespaceActivityCheckConfig customConfig = createCustomConfig("org.example.app?");
        final NamespaceActivityCheckConfigProvider underTest =
                NamespaceActivityCheckConfigProvider.of(List.of(customConfig), defaultConfig);

        final ActivityCheckConfig result = underTest.getConfigForNamespace("org.example.app1");

        assertThat(result.getInactiveInterval()).isEqualTo(CUSTOM_INACTIVE_INTERVAL);
        assertThat(result.getDeletedInterval()).isEqualTo(CUSTOM_DELETED_INTERVAL);
    }

    @Test
    public void returnsDefaultConfigWhenSingleCharWildcardDoesNotMatch() {
        final ActivityCheckConfig defaultConfig = createDefaultConfig();
        final NamespaceActivityCheckConfig customConfig = createCustomConfig("org.example.app?");
        final NamespaceActivityCheckConfigProvider underTest =
                NamespaceActivityCheckConfigProvider.of(List.of(customConfig), defaultConfig);

        final ActivityCheckConfig result = underTest.getConfigForNamespace("org.example.app12");

        assertThat(result.getInactiveInterval()).isEqualTo(DEFAULT_INACTIVE_INTERVAL);
        assertThat(result.getDeletedInterval()).isEqualTo(DEFAULT_DELETED_INTERVAL);
    }

    @Test
    public void firstMatchWins() {
        final ActivityCheckConfig defaultConfig = createDefaultConfig();
        final NamespaceActivityCheckConfig firstConfig = createCustomConfig("org.example.first*",
                Duration.ofMinutes(10L), Duration.ofMinutes(1L));
        final NamespaceActivityCheckConfig secondConfig = createCustomConfig("org.example*",
                Duration.ofMinutes(20L), Duration.ofMinutes(2L));
        final NamespaceActivityCheckConfigProvider underTest =
                NamespaceActivityCheckConfigProvider.of(List.of(firstConfig, secondConfig), defaultConfig);

        final ActivityCheckConfig result = underTest.getConfigForNamespace("org.example.first.app");

        assertThat(result.getInactiveInterval()).isEqualTo(Duration.ofMinutes(10L));
        assertThat(result.getDeletedInterval()).isEqualTo(Duration.ofMinutes(1L));
    }

    @Test
    public void secondPatternMatchesWhenFirstDoesNot() {
        final ActivityCheckConfig defaultConfig = createDefaultConfig();
        final NamespaceActivityCheckConfig firstConfig = createCustomConfig("org.example.first*",
                Duration.ofMinutes(10L), Duration.ofMinutes(1L));
        final NamespaceActivityCheckConfig secondConfig = createCustomConfig("org.example*",
                Duration.ofMinutes(20L), Duration.ofMinutes(2L));
        final NamespaceActivityCheckConfigProvider underTest =
                NamespaceActivityCheckConfigProvider.of(List.of(firstConfig, secondConfig), defaultConfig);

        final ActivityCheckConfig result = underTest.getConfigForNamespace("org.example.second.app");

        assertThat(result.getInactiveInterval()).isEqualTo(Duration.ofMinutes(20L));
        assertThat(result.getDeletedInterval()).isEqualTo(Duration.ofMinutes(2L));
    }

    private static ActivityCheckConfig createDefaultConfig() {
        return DefaultActivityCheckConfig.of(ConfigFactory.parseString(
                "activity-check { inactive-interval = 2h, deleted-interval = 5m }"
        ));
    }

    private static NamespaceActivityCheckConfig createCustomConfig(final String namespacePattern) {
        return createCustomConfig(namespacePattern, CUSTOM_INACTIVE_INTERVAL, CUSTOM_DELETED_INTERVAL);
    }

    private static NamespaceActivityCheckConfig createCustomConfig(final String namespacePattern,
            final Duration inactiveInterval, final Duration deletedInterval) {
        return DefaultNamespaceActivityCheckConfig.of(ConfigFactory.parseString(
                "namespace-pattern = \"" + namespacePattern + "\"\n" +
                        "inactive-interval = " + inactiveInterval.toMinutes() + "m\n" +
                        "deleted-interval = " + deletedInterval.toMinutes() + "m"
        ));
    }
}
