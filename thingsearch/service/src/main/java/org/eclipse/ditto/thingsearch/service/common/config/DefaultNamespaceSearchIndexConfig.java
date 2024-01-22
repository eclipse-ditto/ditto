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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;

import static org.eclipse.ditto.base.model.common.ConditionChecker.*;

/**
 * This class is the default implementation of the NamespaceSearchIndex config.
 * It is instantiated for each namespace search index entry containing the namespace definition and the list of search indexes.
 */
public final class DefaultNamespaceSearchIndexConfig implements NamespaceSearchIndexConfig {

    private static final String CONFIG_PATH = "namespace-search-include-fields";

    private final String namespace;

    private final List<String> searchIncludeFields;

    private DefaultNamespaceSearchIndexConfig(final ConfigWithFallback configWithFallback) {

        this.namespace = configWithFallback.getString(NamespaceSearchIndexConfigValue.NAMESPACE.getConfigPath());

        final List<String> fields = configWithFallback.getStringList(NamespaceSearchIndexConfigValue.SEARCH_INCLUDE_FIELDS.getConfigPath());
        if (!fields.isEmpty()) {
            this.searchIncludeFields = Collections.unmodifiableList(new ArrayList<>(fields));
        } else {
            this.searchIncludeFields = List.of();
        }
    }

    private DefaultNamespaceSearchIndexConfig(final String namespace, final Collection<String> fields) {
        this.namespace = namespace;
        this.searchIncludeFields = Collections.unmodifiableList(new ArrayList<>(fields));
    }

    /**
     * Returns an instance of {@code DefaultNamespaceSearchIndexConfig} based on the settings of the specified Config.
     *
     * @param namespace the namespace config passed in the {@code config}.
     * @param config is supposed to provide the config for the issuer at its current level.
     * @return the instance.
     */
    public static DefaultNamespaceSearchIndexConfig of(final Config config) {
        return new DefaultNamespaceSearchIndexConfig(
                ConfigWithFallback.newInstance(config, NamespaceSearchIndexConfigValue.values()));
    }

    /**
     * Returns a new SubjectIssuerConfig based on the provided strings.
     *
     * @param namespace the list of the namespace {@code namespace}.
     * @param fields list of search index strings.
     * @return a new DefaultNamespaceSearchIndexConfig.
     * @throws NullPointerException if {@code namespace} or {@code fields} is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} or {@code fields} is empty.
     */
    public static DefaultNamespaceSearchIndexConfig of(
            final String namespace,
            final Collection<String> fields) {
        checkNotNull(namespace, "namespace");
        checkNotEmpty(namespace, "namespace");
        argumentNotEmpty(fields, "fields");

        return new DefaultNamespaceSearchIndexConfig(namespace, fields);
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public List<String> getSearchIncludeFields() {
        return searchIncludeFields;
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
        return Objects.equals(namespace, that.namespace) && searchIncludeFields.equals(that.searchIncludeFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, searchIncludeFields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "namespace=" + namespace +
                ", searchIncludeFields=" + searchIncludeFields +
                "]";
    }
}