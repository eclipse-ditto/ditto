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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.RegexPatterns;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.policies.model.PolicyId;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

/**
 * Default implementation of {@link NamespacePoliciesConfig}.
 * <p>
 * Reads from the HOCON config path {@value #CONFIG_PATH}, which must be a config object mapping
 * namespace patterns to lists of policy ID strings. Keys may be exact namespaces, prefix wildcards,
 * or {@code *} to match every namespace:
 * <pre>{@code
 * ditto.policies.namespace-policies {
 *   "org.example.devices"  = ["org.example:tenant-root"]          # exact namespace
 *   "org.example.sensors"  = ["org.example:tenant-root", "org.example:audit-policy"]
 *   "org.example.*"        = ["org.example:tenant-root"]          # all namespaces under org.example
 *   "*"                    = ["root:catch-all-policy"]            # every namespace
 * }
 * }</pre>
 * <p>
 * Matching patterns are resolved in deterministic precedence order:
 * exact match first, then prefix wildcards ordered from most specific to least specific,
 * and finally {@code *}.
 * </p>
 * <p>
 * See {@link NamespacePoliciesConfig#namespaceMatchesPattern} for the supported pattern syntax.
 * </p>
 */
@Immutable
public final class DefaultNamespacePoliciesConfig implements NamespacePoliciesConfig {

    static final String CONFIG_PATH = "ditto.policies.namespace-policies";

    private final Map<String, List<PolicyId>> forwardMap;
    private final Map<PolicyId, Set<String>> reverseMap;
    private final List<String> sortedPatterns;

    private DefaultNamespacePoliciesConfig(final Map<String, List<PolicyId>> forwardMap,
            final Map<PolicyId, Set<String>> reverseMap) {
        this.forwardMap = toUnmodifiableForwardMap(forwardMap);
        this.reverseMap = toUnmodifiableReverseMap(reverseMap);
        this.sortedPatterns = this.forwardMap.keySet().stream()
                .sorted(Comparator.comparingInt(DefaultNamespacePoliciesConfig::patternPrecedence).reversed())
                .toList();
    }

    /**
     * Creates a {@code DefaultNamespacePoliciesConfig} from the given root config object.
     * Returns an empty instance if {@value #CONFIG_PATH} is not present in the config.
     *
     * @param config the root config (from {@code actorSystem.settings().config()}).
     * @return the parsed instance.
     * @throws DittoConfigError if any configured namespace pattern is invalid.
     */
    public static DefaultNamespacePoliciesConfig of(final Config config) {
        if (!config.hasPath(CONFIG_PATH)) {
            return new DefaultNamespacePoliciesConfig(Collections.emptyMap(), Collections.emptyMap());
        }

        final Config nsPoliciesConfig = config.getConfig(CONFIG_PATH);
        final Map<String, List<PolicyId>> forwardMap = new HashMap<>();
        final Map<PolicyId, Set<String>> reverseMap = new HashMap<>();

        for (final Map.Entry<String, ConfigValue> entry : nsPoliciesConfig.root().entrySet()) {
            final String namespace = entry.getKey();
            final ConfigValue value = entry.getValue();
            patternPrecedence(namespace);

            if (value.valueType() != ConfigValueType.LIST) {
                continue;
            }

            final List<PolicyId> policyIds = new ArrayList<>();
            for (final ConfigValue listEntry : (ConfigList) value) {
                if (listEntry.valueType() == ConfigValueType.STRING) {
                    final PolicyId policyId = PolicyId.of(listEntry.unwrapped().toString());
                    policyIds.add(policyId);
                    reverseMap.computeIfAbsent(policyId, k -> new HashSet<>()).add(namespace);
                }
            }
            if (!policyIds.isEmpty()) {
                forwardMap.put(namespace, Collections.unmodifiableList(policyIds));
            }
        }

        return new DefaultNamespacePoliciesConfig(forwardMap, reverseMap);
    }

    @Override
    public Map<String, List<PolicyId>> getNamespacePolicies() {
        return forwardMap;
    }

    @Override
    public List<PolicyId> getRootPoliciesForNamespace(final String namespace) {
        return sortedPatterns.stream()
                .filter(p -> NamespacePoliciesConfig.namespaceMatchesPattern(namespace, p))
                .flatMap(p -> forwardMap.get(p).stream())
                .toList();
    }

    @Override
    public Set<PolicyId> getAllNamespaceRootPolicyIds() {
        return reverseMap.keySet();
    }

    @Override
    public Set<String> getNamespacesForRootPolicy(final PolicyId rootPolicyId) {
        return reverseMap.getOrDefault(rootPolicyId, Collections.emptySet());
    }

    @Override
    public boolean isEmpty() {
        return forwardMap.isEmpty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultNamespacePoliciesConfig that = (DefaultNamespacePoliciesConfig) o;
        return Objects.equals(forwardMap, that.forwardMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(forwardMap);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [namespacePolicies=" + forwardMap + "]";
    }


    private static int patternPrecedence(final String pattern) {
        if ("*".equals(pattern)) {
            return 0;
        }
        if (pattern.endsWith(".*") &&
                RegexPatterns.NAMESPACE_PATTERN.matcher(pattern.substring(0, pattern.length() - 2)).matches()) {
            return pattern.length();
        }
        if (RegexPatterns.NAMESPACE_PATTERN.matcher(pattern).matches()) {
            return Integer.MAX_VALUE;
        }
        throw new DittoConfigError("Unsupported namespace policy pattern <" + pattern + "> at config path <" +
                CONFIG_PATH + ">. Supported syntax is exact namespace, prefix wildcard '<namespace>.*', or '*'.");
    }

    private static Map<String, List<PolicyId>> toUnmodifiableForwardMap(final Map<String, List<PolicyId>> source) {
        final Map<String, List<PolicyId>> result = new HashMap<>();
        source.forEach((namespace, policyIds) -> result.put(namespace, Collections.unmodifiableList(
                new ArrayList<>(policyIds))));
        return Collections.unmodifiableMap(result);
    }

    private static Map<PolicyId, Set<String>> toUnmodifiableReverseMap(final Map<PolicyId, Set<String>> source) {
        final Map<PolicyId, Set<String>> result = new HashMap<>();
        source.forEach((policyId, namespaces) -> result.put(policyId, Collections.unmodifiableSet(
                new HashSet<>(namespaces))));
        return Collections.unmodifiableMap(result);
    }

}
