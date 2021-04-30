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

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.ConfigValueType;

/**
 * Parses a provided VCAP services config string to a {@link com.typesafe.config.Config}.
 * The string is supposed to be valid JSON or HOCON, otherwise a {@link DittoConfigError} is thrown.
 */
@Immutable
final class VcapServicesStringParser implements Function<String, Config> {

    private static final String VCAP_SERVICE_NAME_CONFIG_PATH = "name";

    private static final VcapServicesStringParser INSTANCE = new VcapServicesStringParser();

    private VcapServicesStringParser() {
        super();
    }

    /**
     * Returns an instance of {@code VcapServicesStringToConfigs}.
     *
     * @return the instance.
     */
    static VcapServicesStringParser getInstance() {
        return INSTANCE;
    }

    @Override
    public Config apply(final String systemVcapServices) {
        checkNotNull(systemVcapServices, "system VCAP services string");
        if (systemVcapServices.isEmpty()) {
            return ConfigFactory.empty();
        }

        final Config vcapServicesConfig = tryToParseString(systemVcapServices);
        final Set<Map.Entry<String, ConfigValue>> vcapServicesConfigEntries = vcapServicesConfig.entrySet();

        final Map<String, Object> result = new HashMap<>(vcapServicesConfigEntries.size());
        for (final Map.Entry<String, ConfigValue> serviceConfigEntry : vcapServicesConfigEntries) {
            result.put(serviceConfigEntry.getKey(), convertConfigListToConfigObject(serviceConfigEntry.getValue()));
        }
        return ConfigFactory.parseMap(result);
    }

    private static Config tryToParseString(final String s) {
        try {
            return ConfigFactory.parseString(s);
        } catch (final ConfigException.Parse e) {
            final String msgPattern = "Failed to parse string <{0}>! Is it valid JSON or HOCON?";
            throw new DittoConfigError(MessageFormat.format(msgPattern, s), e);
        }
    }

    private static ConfigValue convertConfigListToConfigObject(final ConfigValue configValue) {
        if (ConfigValueType.LIST == configValue.valueType()) {
            return getAsConfigObject((ConfigList) configValue);
        }
        return configValue;
    }

    private static ConfigObject getAsConfigObject(final ConfigList configList) {
        final Map<String, ConfigValue> flattenedConfigValues = new HashMap<>(configList.size());

        for (int i = 0; i < configList.size(); i++) {
            final ConfigValue configValue = configList.get(i);
            final String configPath;
            if (ConfigValueType.OBJECT == configValue.valueType()) {
                configPath = getName((ConfigObject) configValue);
            } else {
                configPath = String.valueOf(i);
            }
            flattenedConfigValues.put(configPath, configValue);
        }

        return ConfigValueFactory.fromMap(flattenedConfigValues);
    }

    private static String getName(final ConfigObject configObject) {
        final Config config = configObject.toConfig();
        return config.getString(VCAP_SERVICE_NAME_CONFIG_PATH);
    }

}
