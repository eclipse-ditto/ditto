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
package org.eclipse.ditto.internal.utils.pubsub.config;

import java.time.Duration;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Configuration for distributed data update.
 */
public interface PubSubConfig {

    /**
     * @return Seed of hash functions shared across the cluster.
     */
    String getSeed();

    /**
     * @return How long to wait to restart pub-sub if a child actor crashes.
     */
    Duration getRestartDelay();

    /**
     * @return How often to update distributed data.
     */
    Duration getUpdateInterval();

    /**
     * @return How often to sync the distributed data against the cluster state.
     */
    Duration getSyncInterval();

    /**
     * Create a {@code PubSubConfig} object from a {@code Config} object at the key {@code pubsub}.
     *
     * @param config config with path {@code pubsub}.
     * @return the pub-sub config object.
     */
    static PubSubConfig of(final Config config) {
        return DefaultPubSubConfig.of(config);
    }

    /**
     * Create a {@code PubSubConfig} object from the configurations of an actor system at the standard
     * path {@code ditto.pubsub}.
     *
     * @param actorSystem the actor system.
     * @return the pub-sub config object.
     */
    static PubSubConfig of(final ActorSystem actorSystem) {
        final Config dittoScopedConfig =
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config());
        return of(dittoScopedConfig);
    }

    /**
     * An enumeration of the known config path expressions and their associated default values.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Seed to initialize a family of hash functions.
         * Must be identical on all cluster members for pub-sub to work.
         * Rotate when paranoid about collision attacks.
         */
        SEED("seed", "Lorem ipsum dolor sit amet, conectetur adipiscing elit, " +
                "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."),

        /**
         * How long to wait before restarting actors executing pub-sub.
         */
        RESTART_DELAY("restart-delay", Duration.ofSeconds(10L)),

        /**
         * How often to flush local subscriptions to the distributed data replicator.
         */
        UPDATE_INTERVAL("update-interval", Duration.ofSeconds(3L)),

        /**
         * How often to sync distributed data against cluster state.
         * There is an additional random delay between 0 and 100% of the configured value.
         */
        SYNC_INTERVAL("sync-interval", Duration.ofMinutes(5L));

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String path, final Object defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }
    }
}
