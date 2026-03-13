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
package org.eclipse.ditto.internal.utils.pekko.config;

import java.time.Duration;
import java.util.Objects;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link DynamicConfigWatcherConfig}.
 */
final class DefaultDynamicConfigWatcherConfig implements DynamicConfigWatcherConfig {

    private static final String CONFIG_PATH = "ditto.dynamic-config-watcher";
    private static final boolean DEFAULT_ENABLED = false;
    private static final String DEFAULT_FILE_PATH = "/opt/ditto/dynamic-config/dynamic.conf";
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(30);

    private final boolean enabled;
    private final String filePath;
    private final Duration pollInterval;

    private DefaultDynamicConfigWatcherConfig(final boolean enabled, final String filePath,
            final Duration pollInterval) {
        this.enabled = enabled;
        this.filePath = filePath;
        this.pollInterval = pollInterval;
    }

    /**
     * Creates a new {@code DefaultDynamicConfigWatcherConfig} from the given raw config.
     *
     * @param rawConfig the raw config (typically the full ActorSystem config).
     * @return the new instance.
     */
    static DefaultDynamicConfigWatcherConfig of(final Config rawConfig) {
        if (rawConfig.hasPath(CONFIG_PATH)) {
            final Config watcherConfig = rawConfig.getConfig(CONFIG_PATH);
            return new DefaultDynamicConfigWatcherConfig(
                    watcherConfig.hasPath("enabled") ? watcherConfig.getBoolean("enabled") : DEFAULT_ENABLED,
                    watcherConfig.hasPath("file-path") ? watcherConfig.getString("file-path") : DEFAULT_FILE_PATH,
                    watcherConfig.hasPath("poll-interval") ? watcherConfig.getDuration("poll-interval") : DEFAULT_POLL_INTERVAL
            );
        }
        return new DefaultDynamicConfigWatcherConfig(DEFAULT_ENABLED, DEFAULT_FILE_PATH, DEFAULT_POLL_INTERVAL);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getFilePath() {
        return filePath;
    }

    @Override
    public Duration getPollInterval() {
        return pollInterval;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultDynamicConfigWatcherConfig that = (DefaultDynamicConfigWatcherConfig) o;
        return enabled == that.enabled &&
                Objects.equals(filePath, that.filePath) &&
                Objects.equals(pollInterval, that.pollInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, filePath, pollInterval);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", filePath=" + filePath +
                ", pollInterval=" + pollInterval +
                "]";
    }
}
