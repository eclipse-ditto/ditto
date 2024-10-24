/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.common.config;

import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the NamespaceSearchIndex config.
 * It is instantiated for each namespace search index entry containing the namespace definition and the list of search indexes.
 */
@Immutable
public final class DefaultNamespaceSearchIndexConfig implements NamespaceSearchIndexConfig {

    private final String namespacePattern;

    private final List<String> includedFields;

    private DefaultNamespaceSearchIndexConfig(final ConfigWithFallback configWithFallback) {

        this.namespacePattern =
                configWithFallback.getString(NamespaceSearchIndexConfigValue.NAMESPACE_PATTERN.getConfigPath());

        final List<String> fields =
                configWithFallback.getStringList(NamespaceSearchIndexConfigValue.INDEXED_FIELDS.getConfigPath());
        if (!fields.isEmpty()) {
            this.includedFields = List.copyOf(fields);
        } else {
            this.includedFields = List.of();
        }
    }

    /**
     * Returns an instance of {@code DefaultNamespaceSearchIndexConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the config for the issuer at its current level.
     * @return the instance.
     */
    public static DefaultNamespaceSearchIndexConfig of(final Config config) {
        return new DefaultNamespaceSearchIndexConfig(
                ConfigWithFallback.newInstance(config, NamespaceSearchIndexConfigValue.values()));
    }

    @Override
    public String getNamespacePattern() {
        return namespacePattern;
    }

    @Override
    public List<String> getIndexedFields() {
        return includedFields;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultNamespaceSearchIndexConfig that = (DefaultNamespaceSearchIndexConfig) o;
        return Objects.equals(namespacePattern, that.namespacePattern) &&
                includedFields.equals(that.includedFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespacePattern, includedFields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "namespacePattern=" + namespacePattern +
                ", searchIncludeFields=" + includedFields +
                "]";
    }
}