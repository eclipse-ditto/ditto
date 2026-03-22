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

import java.util.function.Function;

import org.apache.pekko.actor.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

/**
 * Utility for lazily polling dynamic config changes via {@link DynamicConfigWatcherExtension}.
 * Encapsulates version tracking, cached config parsing, and error handling.
 *
 * <p>Designed for sharded actors where subscribing to EventStream is not feasible due to the
 * large number of instances. Instead, the config version is checked on demand (e.g., before
 * accessing a config value), and the parsed result is shared across all pollers with the same
 * cache key via {@link DynamicConfigWatcherExtension#getParsedConfig(String, Function)}.</p>
 *
 * <p>Thread-safe: version and value are stored together in an immutable {@code Snapshot} record
 * behind a single volatile reference, so this class can also be used from non-actor contexts
 * (e.g., {@code PreEnforcer} extensions called from multiple threads).</p>
 *
 * @param <T> the parsed config type.
 */
public final class DynamicConfigPoller<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicConfigPoller.class);

    private final DynamicConfigWatcherExtension extension;
    private final String cacheKey;
    private final Function<Config, T> parser;

    @SuppressWarnings("java:S3077") // volatile reference to immutable record is safe publication
    private volatile Snapshot<T> snapshot;

    private record Snapshot<T>(long version, T value) {}

    private DynamicConfigPoller(final DynamicConfigWatcherExtension extension, final String cacheKey,
            final Function<Config, T> parser, final T initialValue) {
        this.extension = extension;
        this.cacheKey = cacheKey;
        this.parser = parser;
        this.snapshot = new Snapshot<>(extension.getVersion(), initialValue);
    }

    /**
     * Creates a new poller.
     *
     * @param system the actor system.
     * @param cacheKey a unique key for the parsed config cache (e.g., {@code "ThingConfigBundle"}).
     * @param parser a function that parses the merged ditto config into the desired type.
     * @param initialValue the initial config value (typically parsed at construction time).
     * @param <T> the parsed config type.
     * @return the new poller.
     */
    public static <T> DynamicConfigPoller<T> of(final ActorSystem system, final String cacheKey,
            final Function<Config, T> parser, final T initialValue) {
        return new DynamicConfigPoller<>(DynamicConfigWatcherExtension.get(system), cacheKey, parser, initialValue);
    }

    /**
     * Returns the current config value, refreshing from the extension if the version has changed.
     * On parse failure, the previous value is retained and a warning is logged.
     *
     * @return the current (possibly refreshed) config value.
     */
    public T get() {
        final long currentVersion = extension.getVersion();
        final Snapshot<T> current = this.snapshot;
        if (currentVersion != current.version) {
            try {
                final T refreshed = extension.getParsedConfig(cacheKey, parser);
                this.snapshot = new Snapshot<>(currentVersion, refreshed);
                LOGGER.info("Refreshed config '{}' from dynamic config version <{}>.", cacheKey, currentVersion);
            } catch (final Exception e) {
                // update version even on failure to avoid retrying on every call
                this.snapshot = new Snapshot<>(currentVersion, current.value);
                LOGGER.warn("Failed to apply dynamic config version <{}> for '{}', keeping previous config: {}",
                        currentVersion, cacheKey, e.getMessage());
            }
        }
        return this.snapshot.value;
    }
}
