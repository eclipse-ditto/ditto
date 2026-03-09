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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;

/**
 * This class is the default implementation of {@link NamespaceAccessConfig}.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class DefaultNamespaceAccessConfig implements NamespaceAccessConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNamespaceAccessConfig.class);
    private static final String NAMESPACE_ACCESS_CONFIG_PATH = "namespace-access";
    private static final List<String> SUPPORTED_RESOURCE_TYPES = List.of(
            ThingConstants.ENTITY_TYPE.toString(),
            PolicyConstants.ENTITY_TYPE.toString()
    );

    private final List<String> conditions;
    private final List<String> allowedNamespaces;
    private final List<String> blockedNamespaces;
    private final List<String> resourceTypes;

    private DefaultNamespaceAccessConfig(final Config config) {
        conditions = config.hasPath("conditions")
                ? Collections.unmodifiableList(config.getStringList("conditions"))
                : Collections.emptyList();
        allowedNamespaces = config.hasPath("allowed-namespaces")
                ? Collections.unmodifiableList(config.getStringList("allowed-namespaces"))
                : Collections.emptyList();
        blockedNamespaces = config.hasPath("blocked-namespaces")
                ? Collections.unmodifiableList(config.getStringList("blocked-namespaces"))
                : Collections.emptyList();
        resourceTypes = config.hasPath("resource-types")
                ? Collections.unmodifiableList(config.getStringList("resource-types"))
                : Collections.emptyList();
        validateResourceTypes(resourceTypes);
    }

    private static void validateResourceTypes(final List<String> resourceTypes) {
        final List<String> unsupportedResourceTypes = resourceTypes.stream()
                .filter(resourceType -> !SUPPORTED_RESOURCE_TYPES.contains(resourceType))
                .distinct()
                .toList();

        if (!unsupportedResourceTypes.isEmpty()) {
            throw new DittoConfigError("Unsupported value(s) for namespace-access resource-types: " +
                    unsupportedResourceTypes + ". Supported values are: " + SUPPORTED_RESOURCE_TYPES);
        }
    }

    /**
     * Returns an instance of {@code DefaultNamespaceAccessConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings for namespace access config.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultNamespaceAccessConfig of(final Config config) {
        return new DefaultNamespaceAccessConfig(config);
    }

    /**
     * Loads a list of {@link NamespaceAccessConfig} from the specified configuration.
     * The configuration is expected to be a list of objects at the path "namespace-access".
     *
     * @param config the configuration to load from.
     * @return an unmodifiable list of namespace access configurations.
     */
    public static List<NamespaceAccessConfig> ofList(final Config config) {
        if (!config.hasPath(NAMESPACE_ACCESS_CONFIG_PATH)) {
            LOGGER.info("No namespace-access configuration found");
            return Collections.emptyList();
        }

        try {
            final List<NamespaceAccessConfig> result;
            final List<? extends ConfigObject> configList = config.getObjectList(NAMESPACE_ACCESS_CONFIG_PATH);
            result = Collections.unmodifiableList(
                    configList.stream()
                            .map(ConfigObject::toConfig)
                            .map(DefaultNamespaceAccessConfig::of)
                            .collect(Collectors.toList())
            );
            LOGGER.info("Successfully loaded {} namespace access config(s)", result.size());
            return result;
        } catch (final Exception e) {
            LOGGER.error("Failed to load namespace-access configuration - aborting to prevent security bypass", e);
            throw new IllegalStateException("Invalid namespace-access configuration: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getConditions() {
        return conditions;
    }

    @Override
    public List<String> getAllowedNamespaces() {
        return allowedNamespaces;
    }

    @Override
    public List<String> getBlockedNamespaces() {
        return blockedNamespaces;
    }

    @Override
    public List<String> getResourceTypes() {
        return resourceTypes;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultNamespaceAccessConfig that = (DefaultNamespaceAccessConfig) o;
        return Objects.equals(conditions, that.conditions) &&
                Objects.equals(allowedNamespaces, that.allowedNamespaces) &&
                Objects.equals(blockedNamespaces, that.blockedNamespaces) &&
                Objects.equals(resourceTypes, that.resourceTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conditions, allowedNamespaces, blockedNamespaces, resourceTypes);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "conditions=" + conditions +
                ", allowedNamespaces=" + allowedNamespaces +
                ", blockedNamespaces=" + blockedNamespaces +
                ", resourceTypes=" + resourceTypes +
                "]";
    }

}
