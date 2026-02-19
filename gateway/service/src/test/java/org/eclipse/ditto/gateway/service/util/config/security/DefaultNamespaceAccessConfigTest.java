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
package org.eclipse.ditto.gateway.service.util.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link DefaultNamespaceAccessConfig}.
 */
public class DefaultNamespaceAccessConfigTest {

    @Test
    public void testLoadConfigWithAllFields() {
        final String configString = """
                {
                    conditions = ["{{ jwt:iss | fn:filter('like','https://eclipse.org*') }}"]
                    allowed-namespaces = ["org.eclipse.*", "concrete.namespace"]
                    blocked-namespaces = ["forbidden.*"]
                }
                """;

        final NamespaceAccessConfig config = DefaultNamespaceAccessConfig.of(
                ConfigFactory.parseString(configString)
        );

        assertThat(config.getConditions())
                .containsExactly("{{ jwt:iss | fn:filter('like','https://eclipse.org*') }}");
        assertThat(config.getAllowedNamespaces())
                .containsExactly("org.eclipse.*", "concrete.namespace");
        assertThat(config.getBlockedNamespaces())
                .containsExactly("forbidden.*");
    }

    @Test
    public void testLoadConfigWithEmptyLists() {
        final String configString = """
                {
                    conditions = []
                    allowed-namespaces = []
                    blocked-namespaces = []
                }
                """;

        final NamespaceAccessConfig config = DefaultNamespaceAccessConfig.of(
                ConfigFactory.parseString(configString)
        );

        assertThat(config.getConditions()).isEmpty();
        assertThat(config.getAllowedNamespaces()).isEmpty();
        assertThat(config.getBlockedNamespaces()).isEmpty();
    }

    @Test
    public void testLoadConfigWithMultipleConditions() {
        final String configString = """
                {
                    conditions = [
                        "{{ jwt:iss | fn:filter('like','https://eclipse.org*') }}",
                        "{{ header:x-tenant | fn:filter('ne','') }}"
                    ]
                    allowed-namespaces = ["org.eclipse.*"]
                    blocked-namespaces = []
                }
                """;

        final NamespaceAccessConfig config = DefaultNamespaceAccessConfig.of(
                ConfigFactory.parseString(configString)
        );

        assertThat(config.getConditions()).hasSize(2);
        assertThat(config.getConditions()).contains(
                "{{ jwt:iss | fn:filter('like','https://eclipse.org*') }}",
                "{{ header:x-tenant | fn:filter('ne','') }}"
        );
    }

    @Test
    public void testLoadConfigWithOnlyAllowedNamespaces() {
        final String configString = """
                {
                    allowed-namespaces = ["org.eclipse.*", "com.example.*"]
                }
                """;

        final NamespaceAccessConfig config = DefaultNamespaceAccessConfig.of(
                ConfigFactory.parseString(configString)
        );

        assertThat(config.getConditions()).isEmpty();
        assertThat(config.getAllowedNamespaces())
                .containsExactly("org.eclipse.*", "com.example.*");
        assertThat(config.getBlockedNamespaces()).isEmpty();
    }

    @Test
    public void testEqualsAndHashCode() {
        final String configString = """
                {
                    conditions = ["{{ jwt:iss }}"]
                    allowed-namespaces = ["org.eclipse.*"]
                    blocked-namespaces = ["test.*"]
                }
                """;

        final NamespaceAccessConfig config1 = DefaultNamespaceAccessConfig.of(
                ConfigFactory.parseString(configString)
        );
        final NamespaceAccessConfig config2 = DefaultNamespaceAccessConfig.of(
                ConfigFactory.parseString(configString)
        );

        assertThat(config1).isEqualTo(config2);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }

    @Test
    public void testToString() {
        final String configString = """
                {
                    conditions = ["{{ jwt:iss }}"]
                    allowed-namespaces = ["org.eclipse.*"]
                    blocked-namespaces = []
                }
                """;

        final NamespaceAccessConfig config = DefaultNamespaceAccessConfig.of(
                ConfigFactory.parseString(configString)
        );

        final String toString = config.toString();
        assertThat(toString).contains("DefaultNamespaceAccessConfig");
        assertThat(toString).contains("conditions");
        assertThat(toString).contains("allowedNamespaces");
    }

}
