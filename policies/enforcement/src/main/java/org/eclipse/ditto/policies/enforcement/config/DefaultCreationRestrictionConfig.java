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

    private static final String RESOURCE_TYPES_CONFIG_PATH = "resource-types";
    private static final String NAMESPACES_CONFIG_PATH = "namespaces";
    private static final String AUTH_SUBJECTS_CONFIG_PATH = "auth-subjects";

    private final Set<String> resourceTypes;
    private final List<Pattern> namespacePatterns;
    private final List<Pattern> authSubjectPatterns;

    private DefaultCreationRestrictionConfig(final ConfigWithFallback configWithFallback) {
        this.resourceTypes = Set.copyOf(configWithFallback.getStringList(RESOURCE_TYPES_CONFIG_PATH));
        this.namespacePatterns = compile(List.copyOf(configWithFallback.getStringList(NAMESPACES_CONFIG_PATH)));
        this.authSubjectPatterns = compile(List.copyOf(configWithFallback.getStringList(AUTH_SUBJECTS_CONFIG_PATH)));
    }

    private static List<Pattern> compile(final List<String> patterns) {
        return patterns.stream()
                .map(expression -> Pattern.compile(LikeHelper.convertToRegexSyntax(expression)))
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
                && authSubjectPatterns.equals(that.authSubjectPatterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceTypes, namespacePatterns, authSubjectPatterns);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "resourceTypes=" + resourceTypes +
                ", namespacePatterns=" + namespacePatterns +
                ", authSubjectPatterns=" + authSubjectPatterns +
                ']';
    }
}
