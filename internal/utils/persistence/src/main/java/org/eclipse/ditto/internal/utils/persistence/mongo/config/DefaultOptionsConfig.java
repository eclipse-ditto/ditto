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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.mongodb.WriteConcern;
import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MongoDbConfig.OptionsConfig}.
 */
@Immutable
public final class DefaultOptionsConfig implements MongoDbConfig.OptionsConfig {

    /**
     * The supposed path of the OptionsConfig within the MongoDB config object.
     */
    static final String CONFIG_PATH = "options";

    private final boolean sslEnabled;
    private final ReadPreference readPreference;
    private final ReadConcern readConcern;
    private final WriteConcern writeConcern;
    private final boolean retryWrites;
    private final Map<String, Object> extraUriOptions;

    private DefaultOptionsConfig(final ScopedConfig config) {
        sslEnabled = config.getBoolean(OptionsConfigValue.SSL_ENABLED.getConfigPath());
        final var readPreferenceString = config.getString(OptionsConfigValue.READ_PREFERENCE.getConfigPath());
        readPreference = ReadPreference.ofReadPreference(readPreferenceString)
                .orElseThrow(() -> {
                    final String msg =
                            MessageFormat.format("Could not parse a ReadPreference from configured string <{0}>",
                                    readPreferenceString);
                    return new DittoConfigError(msg);
                });
        final var readConcernString = config.getString(OptionsConfigValue.READ_CONCERN.getConfigPath());
        readConcern = ReadConcern.ofReadConcern(readConcernString)
                .orElseThrow(() -> {
                    final String msg =
                            MessageFormat.format("Could not parse a ReadConcern from configured string <{0}>",
                                    readConcernString);
                    return new DittoConfigError(msg);
                });
        final var writeConcernString = config.getString(OptionsConfigValue.WRITE_CONCERN.getConfigPath());
        writeConcern = Optional.ofNullable(WriteConcern.valueOf(writeConcernString)).orElseThrow(() -> {
            final String msg =
                    MessageFormat.format("Could not parse a WriteConcern from configured string <{0}>",
                            writeConcernString);
            return new DittoConfigError(msg);
        });
        retryWrites = config.getBoolean(OptionsConfigValue.RETRY_WRITES.getConfigPath());
        extraUriOptions = Collections.unmodifiableMap(new HashMap<>(
                configToMap(config.getConfig(OptionsConfigValue.EXTRA_URI_OPTIONS.getConfigPath()))
        ));
    }

    /**
     * Returns an instance of {@code DefaultOptionsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the options config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws DittoConfigError if {@code config} is invalid.
     */
    public static DefaultOptionsConfig of(final Config config) {
        return new DefaultOptionsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, OptionsConfigValue.values()));
    }

    private static Map<String, Object> configToMap(final Config config) {
        return config.root().unwrapped();
    }

    @Override
    public boolean isSslEnabled() {
        return sslEnabled;
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
    public WriteConcern writeConcern() {
        return writeConcern;
    }

    @Override
    public boolean isRetryWrites() {
        return retryWrites;
    }

    @Override
    public Map<String, Object> extraUriOptions() {
        return extraUriOptions;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultOptionsConfig that = (DefaultOptionsConfig) o;
        return sslEnabled == that.sslEnabled && retryWrites == that.retryWrites &&
                readPreference == that.readPreference &&
                readConcern == that.readConcern &&
                Objects.equals(writeConcern, that.writeConcern) &&
                Objects.equals(extraUriOptions, that.extraUriOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sslEnabled, readPreference, readConcern, writeConcern, retryWrites, extraUriOptions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "sslEnabled=" + sslEnabled +
                ", readPreference=" + readPreference +
                ", readConcern=" + readConcern +
                ", writeConcern=" + writeConcern +
                ", retryWrites=" + retryWrites +
                ", extraUriOptions=" + extraUriOptions +
                "]";
    }
}
