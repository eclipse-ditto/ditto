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
package org.eclipse.ditto.internal.utils.persistence.operations;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link PersistenceOperationsConfig}.
 */
@Immutable
public final class DefaultPersistenceOperationsConfig implements PersistenceOperationsConfig {

    private static final String CONFIG_PATH = "persistence.operations";

    private final Duration delayAfterPersistenceActorShutdown;

    private DefaultPersistenceOperationsConfig(final ConfigWithFallback configWithFallback) {
        delayAfterPersistenceActorShutdown = configWithFallback.getNonNegativeDurationOrThrow(
                PersistenceOperationsConfigValue.DELAY_AFTER_PERSISTENCE_ACTOR_SHUTDOWN);
    }

    /**
     * Returns an instance of {@code DefaultPersistenceOperationsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the persistence operations config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is {@code null} or invalid.
     */
    public static DefaultPersistenceOperationsConfig of(final Config config) {
        return new DefaultPersistenceOperationsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, PersistenceOperationsConfigValue.values()));
    }

    @Override
    public Duration getDelayAfterPersistenceActorShutdown() {
        return delayAfterPersistenceActorShutdown;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPersistenceOperationsConfig that = (DefaultPersistenceOperationsConfig) o;
        return Objects.equals(delayAfterPersistenceActorShutdown, that.delayAfterPersistenceActorShutdown);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delayAfterPersistenceActorShutdown);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "delayAfterPersistenceActorShutdown=" + delayAfterPersistenceActorShutdown +
                "]";
    }

}
