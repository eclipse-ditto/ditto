/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ReadConcern;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ReadPreference;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link SearchPersistenceConfig}.
 */
@Immutable
public final class DefaultSearchPersistenceConfig implements SearchPersistenceConfig {

    private static final String CONFIG_PATH = "persistence";

    private final ReadPreference readPreference;
    private final ReadConcern readConcern;


    private DefaultSearchPersistenceConfig(final ConfigWithFallback config) {
        final var readPreferenceString =
                config.getString(MongoDbConfig.OptionsConfig.OptionsConfigValue.READ_PREFERENCE.getConfigPath());
        readPreference = ReadPreference.ofReadPreference(readPreferenceString)
                .orElseThrow(() -> {
                    final String msg =
                            MessageFormat.format("Could not parse a ReadPreference from configured string <{0}>",
                                    readPreferenceString);
                    return new DittoConfigError(msg);
                });
        final var readConcernString =
                config.getString(MongoDbConfig.OptionsConfig.OptionsConfigValue.READ_CONCERN.getConfigPath());
        readConcern = ReadConcern.ofReadConcern(readConcernString)
                .orElseThrow(() -> {
                    final String msg =
                            MessageFormat.format("Could not parse a ReadConcern from configured string <{0}>",
                                    readConcernString);
                    return new DittoConfigError(msg);
                });
    }

    /**
     * Returns an instance of DefaultUpdaterPersistenceConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the stream config at {@value CONFIG_PATH}.
     * @return the instance.
     * @throws DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSearchPersistenceConfig of(final Config config) {
        return new DefaultSearchPersistenceConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }


    @Override
    public ReadPreference readPreference() {
        return readPreference;
    }

    @Override
    public ReadConcern readConcern() {
        return readConcern;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultSearchPersistenceConfig that = (DefaultSearchPersistenceConfig) o;
        return readPreference == that.readPreference && readConcern == that.readConcern;
    }

    @Override
    public int hashCode() {
        return Objects.hash(readPreference, readConcern);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "readPreference=" + readPreference +
                ", readConcern=" + readConcern +
                "]";
    }
}
