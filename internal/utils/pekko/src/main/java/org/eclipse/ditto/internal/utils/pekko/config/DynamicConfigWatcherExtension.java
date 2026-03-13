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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.apache.pekko.actor.AbstractExtensionId;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.actor.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

/**
 * Pekko Extension that holds the current merged Ditto configuration and manages the
 * {@link DynamicConfigWatcherActor} for hot-reloading configuration from a file on disk.
 *
 * <p>Actors can read the current config via {@link #getDittoConfig()} at any time.
 * The returned config always reflects the latest merged state of static + dynamic config.
 * Use {@link #getVersion()} for cheap cache invalidation checks.</p>
 */
public final class DynamicConfigWatcherExtension implements Extension {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicConfigWatcherExtension.class);
    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    private final AtomicReference<Config> mergedDittoConfig;
    private final AtomicLong version;
    private final ConcurrentHashMap<String, VersionedValue<?>> parsedConfigCache;
    private final boolean enabled;

    private DynamicConfigWatcherExtension(final ExtendedActorSystem system) {
        final Config rawConfig = system.settings().config();

        // Initialize with static ditto.* config
        final Config staticDittoConfig;
        if (rawConfig.hasPath("ditto")) {
            staticDittoConfig = rawConfig.getConfig("ditto").atKey("ditto");
        } else {
            staticDittoConfig = com.typesafe.config.ConfigFactory.empty();
        }
        this.mergedDittoConfig = new AtomicReference<>(staticDittoConfig);
        this.version = new AtomicLong(0L);
        this.parsedConfigCache = new ConcurrentHashMap<>();

        final DynamicConfigWatcherConfig watcherConfig = DefaultDynamicConfigWatcherConfig.of(rawConfig);
        this.enabled = watcherConfig.isEnabled();

        if (enabled) {
            LOGGER.info("Dynamic config watcher is enabled, watching file <{}> every <{}>.",
                    watcherConfig.getFilePath(), watcherConfig.getPollInterval());
            system.actorOf(
                    DynamicConfigWatcherActor.props(this, watcherConfig, rawConfig),
                    DynamicConfigWatcherActor.ACTOR_NAME
            );
        } else {
            LOGGER.info("Dynamic config watcher is disabled.");
        }
    }

    /**
     * Get this extension from the given actor system.
     *
     * @param system the actor system.
     * @return the extension instance.
     */
    public static DynamicConfigWatcherExtension get(final ActorSystem system) {
        return EXTENSION_ID.get(system);
    }

    /**
     * Returns the current merged {@code ditto.*} configuration. This always returns the latest
     * merged config (static config overridden by dynamic config file).
     *
     * @return the current ditto-scoped config.
     */
    public Config getDittoConfig() {
        return mergedDittoConfig.get();
    }

    /**
     * Returns the current config version. Starts at 0 and is incremented on each reload.
     * Useful for cheap cache invalidation: compare with a locally stored version number.
     *
     * @return the current version.
     */
    public long getVersion() {
        return version.get();
    }

    /**
     * @return whether dynamic config file watching is active.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns a parsed config object for the given key, cached per config version. Only the first caller
     * after a version change actually invokes the parser; all subsequent callers sharing the same key
     * get the cached result. This avoids duplicating parsed config objects across hundreds of thousands
     * of sharded actors.
     *
     * @param key a unique key identifying the parsed config type (e.g., {@code "ThingConfig"}).
     * @param parser a function that parses the merged ditto config into the desired type.
     * @param <T> the parsed config type.
     * @return the parsed config, shared across all callers with the same key and version.
     */
    @SuppressWarnings("unchecked")
    public <T> T getParsedConfig(final String key, final Function<Config, T> parser) {
        final long currentVersion = version.get();
        final VersionedValue<?> cached = parsedConfigCache.get(key);
        if (cached != null && cached.version == currentVersion) {
            return (T) cached.value;
        }
        // On version change, compute atomically per key — only one thread parses
        final VersionedValue<?> computed = parsedConfigCache.compute(key, (k, existing) -> {
            if (existing != null && existing.version == currentVersion) {
                return existing;
            }
            return new VersionedValue<>(currentVersion, parser.apply(getDittoConfig()));
        });
        return (T) computed.value;
    }

    /**
     * Updates the merged config and increments the version. Called by {@link DynamicConfigWatcherActor}.
     *
     * @param newDittoConfig the new merged ditto config.
     * @return the new version number.
     */
    long updateConfig(final Config newDittoConfig) {
        mergedDittoConfig.set(newDittoConfig);
        return version.incrementAndGet();
    }

    private record VersionedValue<T>(long version, T value) {}

    private static final class ExtensionId extends AbstractExtensionId<DynamicConfigWatcherExtension> {

        @Override
        public DynamicConfigWatcherExtension createExtension(final ExtendedActorSystem system) {
            return new DynamicConfigWatcherExtension(system);
        }
    }
}
