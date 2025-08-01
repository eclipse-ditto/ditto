/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.LikeHelper;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class implements {@link CreationRestrictionConfig} for Ditto's Concierge service.
 */
@Immutable
public final class DefaultCreationRestrictionConfig implements CreationRestrictionConfig {

    private final Set<String> resourceTypes;
    private final List<Pattern> namespacePatterns;
    private final List<Pattern> authSubjectPatterns;
    private final List<Pattern> thingDefinitionPatterns;

    private DefaultCreationRestrictionConfig(final ConfigWithFallback configWithFallback) {
        this.resourceTypes = Set.copyOf(configWithFallback.getStringList(
                CreationRestrictionConfigValues.RESOURCE_TYPES.getConfigPath()
        ));
        this.namespacePatterns = compile(List.copyOf(configWithFallback.getStringList(
                CreationRestrictionConfigValues.NAMESPACES.getConfigPath())
        ));
        this.authSubjectPatterns = compile(List.copyOf(configWithFallback.getStringList(
                CreationRestrictionConfigValues.AUTH_SUBJECTS.getConfigPath())
        ));
        this.thingDefinitionPatterns = compile(Collections.unmodifiableList(
                toStringListWithNulls(
                        configWithFallback.getAnyRefList(
                                CreationRestrictionConfigValues.THING_DEFINITIONS.getConfigPath()
                        )
                )
        ), true);
    }

    private List<String> toStringListWithNulls(final List<?> anyRefList) {
        return anyRefList.stream()
                .map(any -> any == null ? null : String.valueOf(any))
                .toList();
    }

    private static List<Pattern> compile(final List<String> patterns) {
        return compile(patterns, false);
    }

    private static List<Pattern> compile(final List<String> patterns, final boolean keepNullValues) {
        return patterns.stream()
                .map(LikeHelper::convertToRegexSyntax)
                .filter(str -> keepNullValues || Objects.nonNull(str))
                .map(str -> keepNullValues && str == null ? null : Pattern.compile(str))
                .toList();
    }

    /**
     * Returns an instance of {@code CreationRestrictionConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the restriction config.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static CreationRestrictionConfig of(final Config config) {
        return new DefaultCreationRestrictionConfig(ConfigWithFallback.newInstance(config,
                CreationRestrictionConfig.CreationRestrictionConfigValues.values()));
    }

    @Override
    public Set<String> getResourceTypes() {
        return resourceTypes;
    }

    @Override
    public List<Pattern> getNamespace() {
        return namespacePatterns;
    }

    public List<Pattern> getAuthSubject() {
        return authSubjectPatterns;
    }

    @Override
    public List<Pattern> getThingDefinitions() {
        return thingDefinitionPatterns;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultCreationRestrictionConfig that = (DefaultCreationRestrictionConfig) o;
        return resourceTypes.equals(that.resourceTypes)
                && namespacePatterns.equals(that.namespacePatterns)
                && authSubjectPatterns.equals(that.authSubjectPatterns)
                && thingDefinitionPatterns.equals(that.thingDefinitionPatterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceTypes, namespacePatterns, authSubjectPatterns, thingDefinitionPatterns);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "resourceTypes=" + resourceTypes +
                ", namespacePatterns=" + namespacePatterns +
                ", authSubjectPatterns=" + authSubjectPatterns +
                ", thingDefinitionPatterns=" + thingDefinitionPatterns +
                ']';
    }
}
