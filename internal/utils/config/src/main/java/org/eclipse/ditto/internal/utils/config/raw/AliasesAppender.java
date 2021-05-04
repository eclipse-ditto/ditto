/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.config.raw;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

/**
 * Extends a given {@link com.typesafe.config.Config} by the same config values using aliases for the original config
 * keys.
 * The aliases are derived from the JSON object string which is supposed to be set as system environment variable with
 * name {@value CONFIG_ALIASES_ENV_VARIABLE_NAME}.
 */
@Immutable
final class AliasesAppender implements UnaryOperator<Config> {

    /**
     * Name of the system environment variable for setting the config aliases.
     * The config aliases are supposed to be a JSON object string.
     */
    static final String CONFIG_ALIASES_ENV_VARIABLE_NAME = "CONFIG_ALIASES";

    private static final Map<String, String> DEFAULT_CONFIG_ALIASES = Collections.emptyMap();
    private static final Logger LOGGER = LoggerFactory.getLogger(AliasesAppender.class);

    private final Map<String, String> systemConfigAliases;

    private AliasesAppender(final Map<String, String> theSystemConfigAliases) {
        systemConfigAliases = theSystemConfigAliases;
    }

    /**
     * Returns an instance of {@code AliasesAsConfigSupplier}.
     * The returned instance relies on the system environment variable {@value #CONFIG_ALIASES_ENV_VARIABLE_NAME} for
     * determining the aliases.
     *
     * @return the instance.
     */
    static AliasesAppender getInstance() {
        return new AliasesAppender(parseString(getSystemConfigAliasesOrNull()));
    }

    @Nullable
    private static String getSystemConfigAliasesOrNull() {
        return System.getenv(CONFIG_ALIASES_ENV_VARIABLE_NAME);
    }

    private static Map<String, String> parseString(@Nullable final String systemConfigAliasesJsonObjectString) {
        if (null == systemConfigAliasesJsonObjectString || systemConfigAliasesJsonObjectString.isEmpty()) {
            LOGGER.info("Environment variable <{}> is not defined or empty, using default config aliases: {}.",
                    CONFIG_ALIASES_ENV_VARIABLE_NAME, DEFAULT_CONFIG_ALIASES);
            return DEFAULT_CONFIG_ALIASES;
        }

        final JsonObject systemConfigAliasesAsJsonObject = JsonObject.of(systemConfigAliasesJsonObjectString);
        final Map<String, String> result = new HashMap<>(systemConfigAliasesAsJsonObject.getSize());
        for (final JsonField jsonField : systemConfigAliasesAsJsonObject) {
            final JsonValue value = jsonField.getValue();
            result.put(jsonField.getKeyName(), value.asString());
        }

        LOGGER.info("Environment variable <{}> defines these config aliases: {}.", CONFIG_ALIASES_ENV_VARIABLE_NAME,
                result);

        return result;
    }

    /**
     * @throws java.lang.NullPointerException if {@code vcapConfig} is {@code null}.
     */
    @Override
    public Config apply(final Config vcapConfig) {
        checkNotNull(vcapConfig, "VCAP config");
        if (systemConfigAliases.isEmpty()) {
            return vcapConfig;
        }

        final Map<String, ConfigValue> aliasesConfigMap = new HashMap<>(systemConfigAliases.size());

        for (final Map.Entry<String, String> systemConfigAlias : systemConfigAliases.entrySet()) {
            final String alias = systemConfigAlias.getKey();
            final String originalKey = systemConfigAlias.getValue();
            @Nullable final ConfigValue originalValue = tryToGetOriginalValueOrNull(vcapConfig, originalKey, alias);
            if (null != originalValue) {
                aliasesConfigMap.put(alias, originalValue);
            }
        }

        return vcapConfig.withFallback(ConfigFactory.parseMap(aliasesConfigMap));
    }

    @Nullable
    private static ConfigValue tryToGetOriginalValueOrNull(final Config vcapConfig, final String originalKey,
            final String alias) {

        try {
            return getOriginalValueOrNull(vcapConfig, originalKey);
        } catch (final ConfigException e) {
            LOGGER.warn("Failed to retrieve value for creating alias {} -> {}!", originalKey, alias, e);
            return null;
        }
    }

    private static ConfigValue getOriginalValueOrNull(final Config vcapConfig, final String originalKey) {
        return vcapConfig.getValue(originalKey);
    }

}
