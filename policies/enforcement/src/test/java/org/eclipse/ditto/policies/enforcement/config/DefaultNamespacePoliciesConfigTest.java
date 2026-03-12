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
package org.eclipse.ditto.policies.enforcement.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.Test;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link DefaultNamespacePoliciesConfig}.
 */
public final class DefaultNamespacePoliciesConfigTest {

    @Test
    public void parsesForwardAndReverseMapsFromHocon() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"org.example.devices\" = [\"org.example:tenant-root\"]\n" +
                "  \"org.example.sensors\" = [\"org.example:tenant-root\", \"org.example:audit-policy\"]\n" +
                "}");

        final var underTest = DefaultNamespacePoliciesConfig.of(config);

        assertThat(underTest.isEmpty()).isFalse();

        // forward map
        assertThat(underTest.getRootPoliciesForNamespace("org.example.devices"))
                .containsExactly(PolicyId.of("org.example:tenant-root"));
        assertThat(underTest.getRootPoliciesForNamespace("org.example.sensors"))
                .containsExactly(PolicyId.of("org.example:tenant-root"), PolicyId.of("org.example:audit-policy"));
        assertThat(underTest.getRootPoliciesForNamespace("org.example.unknown")).isEmpty();

        // reverse map
        assertThat(underTest.getAllNamespaceRootPolicyIds())
                .containsExactlyInAnyOrder(PolicyId.of("org.example:tenant-root"),
                        PolicyId.of("org.example:audit-policy"));
        assertThat(underTest.getNamespacesForRootPolicy(PolicyId.of("org.example:tenant-root")))
                .containsExactlyInAnyOrder("org.example.devices", "org.example.sensors");
        assertThat(underTest.getNamespacesForRootPolicy(PolicyId.of("org.example:audit-policy")))
                .containsExactlyInAnyOrder("org.example.sensors");
    }

    @Test
    public void returnsEmptyInstanceWhenConfigPathAbsent() {
        final var underTest = DefaultNamespacePoliciesConfig.of(ConfigFactory.empty());

        assertThat(underTest.isEmpty()).isTrue();
        assertThat(underTest.getNamespacePolicies()).isEmpty();
        assertThat(underTest.getAllNamespaceRootPolicyIds()).isEmpty();
        assertThat(underTest.getRootPoliciesForNamespace("any.namespace")).isEmpty();
        assertThat(underTest.getNamespacesForRootPolicy(PolicyId.of("org.example:some-policy"))).isEmpty();
    }

    @Test
    public void skipsNonListConfigValues() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"org.example.devices\" = [\"org.example:tenant-root\"]\n" +
                "  \"org.example.badentry\" = \"not-a-list\"\n" +
                "}");

        final var underTest = DefaultNamespacePoliciesConfig.of(config);

        assertThat(underTest.getRootPoliciesForNamespace("org.example.devices")).hasSize(1);
        assertThat(underTest.getRootPoliciesForNamespace("org.example.badentry")).isEmpty();
    }

    @Test
    public void skipsEmptyListEntries() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"org.example.devices\" = [\"org.example:tenant-root\"]\n" +
                "  \"org.example.empty\" = []\n" +
                "}");

        final var underTest = DefaultNamespacePoliciesConfig.of(config);

        assertThat(underTest.getRootPoliciesForNamespace("org.example.devices")).hasSize(1);
        assertThat(underTest.getRootPoliciesForNamespace("org.example.empty")).isEmpty();
        // empty lists must not appear in the forward map
        assertThat(underTest.getNamespacePolicies()).doesNotContainKey("org.example.empty");
    }

    @Test
    public void prefixWildcardMatchesNamespacesUnderPrefix() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"org.example.*\" = [\"org.example:tenant-root\"]\n" +
                "}");

        final var underTest = DefaultNamespacePoliciesConfig.of(config);

        assertThat(underTest.getRootPoliciesForNamespace("org.example.devices"))
                .containsExactly(PolicyId.of("org.example:tenant-root"));
        assertThat(underTest.getRootPoliciesForNamespace("org.example.sensors"))
                .containsExactly(PolicyId.of("org.example:tenant-root"));
        assertThat(underTest.getRootPoliciesForNamespace("org.examples")).isEmpty();
        assertThat(underTest.getRootPoliciesForNamespace("com.other.ns")).isEmpty();
    }

    @Test
    public void catchAllWildcardMatchesEveryNamespace() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"*\" = [\"root:catch-all\"]\n" +
                "}");

        final var underTest = DefaultNamespacePoliciesConfig.of(config);

        assertThat(underTest.getRootPoliciesForNamespace("org.example.devices"))
                .containsExactly(PolicyId.of("root:catch-all"));
        assertThat(underTest.getRootPoliciesForNamespace("com.completely.different"))
                .containsExactly(PolicyId.of("root:catch-all"));
    }

    @Test
    public void exactAndWildcardPoliciesAreCombinedForMatchingNamespace() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"org.example.devices\" = [\"org.example:device-root\"]\n" +
                "  \"org.example.*\"       = [\"org.example:tenant-root\"]\n" +
                "}");

        final var underTest = DefaultNamespacePoliciesConfig.of(config);

        assertThat(underTest.getRootPoliciesForNamespace("org.example.devices"))
                .containsExactly(
                        PolicyId.of("org.example:device-root"),
                        PolicyId.of("org.example:tenant-root"));
        assertThat(underTest.getRootPoliciesForNamespace("org.example.sensors"))
                .containsExactly(PolicyId.of("org.example:tenant-root"));
    }

    @Test
    public void rejectsUnsupportedWildcardInMiddleOfPattern() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"org.*.devices\" = [\"org.example:tenant-root\"]\n" +
                "}");

        assertThatThrownBy(() -> DefaultNamespacePoliciesConfig.of(config))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("Unsupported namespace policy pattern <org.*.devices>");
    }

    @Test
    public void rejectsUnsupportedWildcardSuffixPattern() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"foo*\" = [\"org.example:tenant-root\"]\n" +
                "}");

        assertThatThrownBy(() -> DefaultNamespacePoliciesConfig.of(config))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("Unsupported namespace policy pattern <foo*>");
    }

    @Test
    public void rejectsInvalidCatchAllLikePattern() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"**\" = [\"org.example:tenant-root\"]\n" +
                "}");

        assertThatThrownBy(() -> DefaultNamespacePoliciesConfig.of(config))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("Unsupported namespace policy pattern <**>");
    }

    @Test
    public void matchingPoliciesAreReturnedInDeterministicPrecedenceOrder() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"*\"                     = [\"root:catch-all\"]\n" +
                "  \"org.example.*\"         = [\"org.example:tenant-root\"]\n" +
                "  \"org.example.devices.*\" = [\"org.example:devices-root\"]\n" +
                "  \"org.example.devices\"   = [\"org.example:device-root\"]\n" +
                "}");

        final var underTest = DefaultNamespacePoliciesConfig.of(config);

        assertThat(underTest.getRootPoliciesForNamespace("org.example.devices"))
                .containsExactly(
                        PolicyId.of("org.example:device-root"),
                        PolicyId.of("org.example:tenant-root"),
                        PolicyId.of("root:catch-all"));
        assertThat(underTest.getRootPoliciesForNamespace("org.example.devices.alpha"))
                .containsExactly(
                        PolicyId.of("org.example:devices-root"),
                        PolicyId.of("org.example:tenant-root"),
                        PolicyId.of("root:catch-all"));
    }

    @Test
    public void moreSpecificPrefixWildcardPrecedesBroaderPrefixWildcard() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"org.example.*\"         = [\"org.example:tenant-root\"]\n" +
                "  \"org.example.devices.*\" = [\"org.example:devices-root\"]\n" +
                "}");

        final var underTest = DefaultNamespacePoliciesConfig.of(config);

        assertThat(underTest.getRootPoliciesForNamespace("org.example.devices.alpha"))
                .containsExactly(
                        PolicyId.of("org.example:devices-root"),
                        PolicyId.of("org.example:tenant-root"));
    }

    @Test
    public void returnedMapsAreImmutable() {
        final var config = ConfigFactory.parseString(
                "ditto.policies.namespace-policies {\n" +
                "  \"org.example.devices\" = [\"org.example:tenant-root\"]\n" +
                "}");

        final var underTest = DefaultNamespacePoliciesConfig.of(config);

        assertThatThrownBy(() -> underTest.getNamespacePolicies().put("org.example.new", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> underTest.getAllNamespaceRootPolicyIds().add(PolicyId.of("org.example:new")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() ->
                underTest.getNamespacesForRootPolicy(PolicyId.of("org.example:tenant-root")).add("ns"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

}
