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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;

/**
 * This class is the default implementation of the WoT (Web of Things) Discovery "Thing Directory" config.
 */
@Immutable
public final class DefaultWotDirectoryConfig implements WotDirectoryConfig {

    private static final String CONFIG_PATH = "wot-directory";

    private final String basePath;
    private final JsonObject jsonTemplate;
    private final boolean authenticationRequired;

    private DefaultWotDirectoryConfig(final ScopedConfig scopedConfig) {
        basePath = scopedConfig.getString(ConfigValue.BASE_PREFIX.getConfigPath());
        final String jsonTemplateRendered =
                scopedConfig.getValue(ConfigValue.JSON_TEMPLATE.getConfigPath()).render(ConfigRenderOptions.concise());
        final String jsonTemplateStrippedStartEndQuotes =
                (jsonTemplateRendered.startsWith("\"{") && jsonTemplateRendered.endsWith("}\"")) ?
                        jsonTemplateRendered.substring(1, jsonTemplateRendered.length() - 1) : jsonTemplateRendered;
        final String replaceEscapedQuotesJsonTemplate = jsonTemplateStrippedStartEndQuotes.replace("\\\"", "\"");
        jsonTemplate = JsonFactory.readFrom(replaceEscapedQuotesJsonTemplate).asObject();
        authenticationRequired = scopedConfig.getBoolean(ConfigValue.AUTHENTICATION_REQUIRED.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultCloudEventsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the public health config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultWotDirectoryConfig of(final Config config) {
        return new DefaultWotDirectoryConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, WotDirectoryConfig.ConfigValue.values()));
    }

    @Override
    public String getBasePrefix() {
        return basePath;
    }

    @Override
    public JsonObject getJsonTemplate() {
        return jsonTemplate;
    }

    @Override
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultWotDirectoryConfig that = (DefaultWotDirectoryConfig) o;
        return authenticationRequired == that.authenticationRequired &&
                basePath.equals(that.basePath) &&
                jsonTemplate.equals(that.jsonTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(basePath, jsonTemplate, authenticationRequired);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "basePath=" + basePath +
                ", jsonTemplate=" + jsonTemplate +
                ", authenticationRequired=" + authenticationRequired +
                "]";
    }

}
