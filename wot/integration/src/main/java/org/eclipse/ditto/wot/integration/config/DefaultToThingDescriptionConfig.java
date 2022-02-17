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
package org.eclipse.ditto.wot.integration.config;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;

/**
 * This class is the default implementation of the WoT (Web of Things) {@link ToThingDescriptionConfig}.
 */
@Immutable
final class DefaultToThingDescriptionConfig implements ToThingDescriptionConfig {

    private static final String CONFIG_PATH = "to-thing-description";

    private final String basePrefix;
    private final JsonObject jsonTemplate;
    private final Map<String, JsonValue> placeholders;
    private final boolean addCreated;
    private final boolean addModified;

    private DefaultToThingDescriptionConfig(final ScopedConfig scopedConfig) {
        basePrefix = scopedConfig.getString(ConfigValue.BASE_PREFIX.getConfigPath());
        jsonTemplate = JsonFactory.readFrom(
                scopedConfig.getValue(ConfigValue.JSON_TEMPLATE.getConfigPath()).render(ConfigRenderOptions.concise())
        ).asObject();
        placeholders = JsonFactory.readFrom(
                scopedConfig.getValue(ConfigValue.PLACEHOLDERS.getConfigPath()).render(ConfigRenderOptions.concise())
        ).asObject()
                .stream()
                .collect(Collectors.toMap(f -> f.getKey().toString(), JsonField::getValue));
        addCreated = scopedConfig.getBoolean(ConfigValue.ADD_CREATED.getConfigPath());
        addModified = scopedConfig.getBoolean(ConfigValue.ADD_MODIFIED.getConfigPath());
    }

    /**
     * Returns an instance of the thing config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the thing config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultToThingDescriptionConfig of(final Config config) {
        return new DefaultToThingDescriptionConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                ConfigValue.values()));
    }

    @Override
    public String getBasePrefix() {
        return basePrefix;
    }

    @Override
    public JsonObject getJsonTemplate() {
        return jsonTemplate;
    }

    @Override
    public Map<String, JsonValue> getPlaceholders() {
        return placeholders;
    }

    @Override
    public boolean addCreated() {
        return addCreated;
    }

    @Override
    public boolean addModified() {
        return addModified;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultToThingDescriptionConfig that = (DefaultToThingDescriptionConfig) o;
        return addCreated == that.addCreated && addModified == that.addModified &&
                Objects.equals(basePrefix, that.basePrefix) &&
                Objects.equals(jsonTemplate, that.jsonTemplate) &&
                Objects.equals(placeholders, that.placeholders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(basePrefix, jsonTemplate, placeholders, addCreated, addModified);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "basePrefix=" + basePrefix +
                ", jsonTemplate=" + jsonTemplate +
                ", placeholders=" + placeholders +
                ", addCreated=" + addCreated +
                ", addModified=" + addModified +
                "]";
    }
}
