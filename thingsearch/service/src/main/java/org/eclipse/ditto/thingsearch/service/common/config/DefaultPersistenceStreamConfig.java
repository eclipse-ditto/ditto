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
package org.eclipse.ditto.thingsearch.service.common.config;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.mongodb.WriteConcern;
import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link PersistenceStreamConfig}.
 */
@Immutable
public final class DefaultPersistenceStreamConfig implements PersistenceStreamConfig {

    static final String CONFIG_PATH = "persistence";

    private final Duration ackDelay;
    private final WriteConcern withAcknowledgementsWriteConcern;
    private final DefaultStreamStageConfig defaultStreamStageConfig;

    private DefaultPersistenceStreamConfig(final ConfigWithFallback persistenceStreamScopedConfig,
            final DefaultStreamStageConfig defaultStreamStageConfig) {

        ackDelay = persistenceStreamScopedConfig.getNonNegativeDurationOrThrow(PersistenceStreamConfigValue.ACK_DELAY);
        final var writeConcernString = persistenceStreamScopedConfig.getString(
                PersistenceStreamConfigValue.WITH_ACKS_WRITE_CONCERN.getConfigPath());
        withAcknowledgementsWriteConcern = Optional.of(WriteConcern.valueOf(writeConcernString))
                .orElseThrow(() -> {
                    final String msg =
                            MessageFormat.format("Could not parse a WriteConcern from configured string <{0}>",
                                    writeConcernString);
                    return new DittoConfigError(msg);
                });
        this.defaultStreamStageConfig = defaultStreamStageConfig;
    }

    /**
     * Returns an instance of DefaultPersistenceStreamConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the persistence stream config at {@value CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultPersistenceStreamConfig of(final Config config) {
        return new DefaultPersistenceStreamConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, PersistenceStreamConfigValue.values()),
                DefaultStreamStageConfig.getInstance(config, CONFIG_PATH));
    }

    @Override
    public Duration getAckDelay() {
        return ackDelay;
    }

    @Override
    public WriteConcern getWithAcknowledgementsWriteConcern() {
        return withAcknowledgementsWriteConcern;
    }

    @Override
    public int getParallelism() {
        return defaultStreamStageConfig.getParallelism();
    }

    @Override
    public ExponentialBackOffConfig getExponentialBackOffConfig() {
        return defaultStreamStageConfig.getExponentialBackOffConfig();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPersistenceStreamConfig that = (DefaultPersistenceStreamConfig) o;
        return Objects.equals(ackDelay, that.ackDelay) &&
                Objects.equals(withAcknowledgementsWriteConcern, that.withAcknowledgementsWriteConcern) &&
                Objects.equals(defaultStreamStageConfig, that.defaultStreamStageConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ackDelay, withAcknowledgementsWriteConcern, defaultStreamStageConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "ackDelay=" + ackDelay +
                ", withAcknowledgementsWriteConcern=" + withAcknowledgementsWriteConcern +
                ", defaultStreamStageConfig=" + defaultStreamStageConfig +
                "]";
    }

}
