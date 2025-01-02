/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.common.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.LikeHelper;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.json.JsonFieldSelector;

import com.typesafe.config.Config;

/**
 * This class implements {@link PreDefinedExtraFieldsConfig}.
 */
@Immutable
public final class DefaultPreDefinedExtraFieldsConfig implements PreDefinedExtraFieldsConfig {

    private final List<Pattern> namespacePatterns;
    @Nullable private final String rqlCondition;
    private final JsonFieldSelector extraFields;

    private DefaultPreDefinedExtraFieldsConfig(final ConfigWithFallback config) {
        this.namespacePatterns = compile(List.copyOf(config.getStringList(
                PreDefinedExtraFieldsConfig.ConfigValues.NAMESPACES.getConfigPath())
        ));
        this.rqlCondition = config.getStringOrNull(ConfigValues.CONDITION);
        final List<String> configuredExtraFields = config.getStringList(ConfigValues.EXTRA_FIELDS.getConfigPath());
        this.extraFields = JsonFieldSelector.newInstance(
                configuredExtraFields.getFirst(),
                configuredExtraFields.subList(1, configuredExtraFields.size()).toArray(CharSequence[]::new)
        );
    }

    /**
     * Returns an instance of {@code CreationRestrictionConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the restriction config.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultPreDefinedExtraFieldsConfig of(final Config config) {
        return new DefaultPreDefinedExtraFieldsConfig(ConfigWithFallback.newInstance(config,
                PreDefinedExtraFieldsConfig.ConfigValues.values()));
    }

    private static List<Pattern> compile(final List<String> patterns) {
        return patterns.stream()
                .map(LikeHelper::convertToRegexSyntax)
                .filter(Objects::nonNull)
                .map(Pattern::compile)
                .toList();
    }

    @Override
    public List<Pattern> getNamespace() {
        return namespacePatterns;
    }

    @Override
    public Optional<String> getCondition() {
        return Optional.ofNullable(rqlCondition);
    }

    @Override
    public JsonFieldSelector getExtraFields() {
        return extraFields;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof final DefaultPreDefinedExtraFieldsConfig that)) {
            return false;
        }
        return Objects.equals(namespacePatterns, that.namespacePatterns) &&
                Objects.equals(rqlCondition, that.rqlCondition) &&
                Objects.equals(extraFields, that.extraFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespacePatterns, rqlCondition, extraFields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "namespacePatterns=" + namespacePatterns +
                ", rqlCondition='" + rqlCondition + '\'' +
                ", extraFields=" + extraFields +
                "]";
    }
}
