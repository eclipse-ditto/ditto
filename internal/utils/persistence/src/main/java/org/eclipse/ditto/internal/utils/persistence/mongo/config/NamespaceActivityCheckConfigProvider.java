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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.LikeHelper;

/**
 * Provider for namespace-specific activity check configurations.
 * Compiles namespace patterns to regex at construction time and provides the appropriate
 * configuration for a given namespace.
 *
 * @since 3.9.0
 */
@Immutable
public final class NamespaceActivityCheckConfigProvider {

    private final List<CompiledNamespaceConfig> compiledConfigs;
    private final ActivityCheckConfig defaultConfig;

    private NamespaceActivityCheckConfigProvider(
            final List<CompiledNamespaceConfig> compiledConfigs,
            final ActivityCheckConfig defaultConfig) {
        this.compiledConfigs = compiledConfigs;
        this.defaultConfig = defaultConfig;
    }

    /**
     * Creates a new instance of {@code NamespaceActivityCheckConfigProvider}.
     *
     * @param namespaceConfigs the list of namespace-specific activity check configurations.
     * @param defaultConfig the default activity check configuration to use when no namespace pattern matches.
     * @return the new instance.
     */
    public static NamespaceActivityCheckConfigProvider of(
            final List<NamespaceActivityCheckConfig> namespaceConfigs,
            final ActivityCheckConfig defaultConfig) {

        checkNotNull(namespaceConfigs, "namespaceConfigs");
        checkNotNull(defaultConfig, "defaultConfig");

        final List<CompiledNamespaceConfig> compiledConfigs = new ArrayList<>(namespaceConfigs.size());
        for (final NamespaceActivityCheckConfig config : namespaceConfigs) {
            final String pattern = config.getNamespacePattern();
            if (pattern != null && !pattern.isEmpty()) {
                final String regex = LikeHelper.convertToRegexSyntax(pattern);
                if (regex != null) {
                    compiledConfigs.add(new CompiledNamespaceConfig(Pattern.compile(regex), config));
                }
            }
        }

        return new NamespaceActivityCheckConfigProvider(List.copyOf(compiledConfigs), defaultConfig);
    }

    /**
     * Returns the activity check configuration for the given namespace.
     * Evaluates namespace patterns in order and returns the first matching configuration.
     * If no pattern matches, returns the default configuration.
     *
     * @param namespace the namespace to get the configuration for.
     * @return the activity check configuration for the namespace.
     */
    public ActivityCheckConfig getConfigForNamespace(@Nullable final String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return defaultConfig;
        }

        for (final CompiledNamespaceConfig compiledConfig : compiledConfigs) {
            if (compiledConfig.pattern.matcher(namespace).matches()) {
                return compiledConfig.config;
            }
        }

        return defaultConfig;
    }

    @Immutable
    private static final class CompiledNamespaceConfig {

        private final Pattern pattern;
        private final NamespaceActivityCheckConfig config;

        private CompiledNamespaceConfig(final Pattern pattern, final NamespaceActivityCheckConfig config) {
            this.pattern = pattern;
            this.config = config;
        }
    }
}
