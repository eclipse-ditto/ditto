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
package org.eclipse.ditto.internal.utils.ddata;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

/**
 * This class is the default implementation of Akka's {@link akka.cluster.ddata.Replicator} config.
 */
class DefaultAkkaReplicatorConfig implements AkkaReplicatorConfig {

    private static final String CONFIG_PATH = "akka-distributed-data";

    private final String name;
    private final String role;
    private final Duration notifySubscribersInterval;
    private final Duration gossipInterval;
    private final Config config;

    private DefaultAkkaReplicatorConfig(final Config config) {
        name = config.getString(AkkaReplicatorConfigValue.NAME.getConfigPath());
        role = config.getString(AkkaReplicatorConfigValue.ROLE.getConfigPath());
        gossipInterval = config.getDuration(AkkaReplicatorConfigValue.GOSSIP_INTERVAL.getConfigPath());
        notifySubscribersInterval =
                config.getDuration(AkkaReplicatorConfigValue.NOTIFY_SUBSCRIBERS_INTERVAL.getConfigPath());
        this.config = config;
    }

    /**
     * Returns an instance of {@code DefaultAkkaReplicatorConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the Replicator config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAkkaReplicatorConfig of(final Config config) {

        return new DefaultAkkaReplicatorConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                AkkaReplicatorConfigValue.values())
        );
    }

    /**
     * Returns an instance of {@code DefaultAkkaReplicatorConfig} based on the settings of the specified Config.
     *
     * @param name the name of the replicator.
     * @param role the cluster role of members with replicas of the distributed collection.
     * @param config is supposed to provide the settings of the Replicator config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAkkaReplicatorConfig of(final Config config, final CharSequence name,
            final CharSequence role) {
        final Map<String, Object> specificConfig = new HashMap<>(2);
        specificConfig.put(AkkaReplicatorConfigValue.NAME.getConfigPath(), checkNotNull(name, "name"));
        specificConfig.put(AkkaReplicatorConfigValue.ROLE.getConfigPath(), checkNotNull(role, "role"));

        // TODO Ditto issue #439: replace ConfigWithFallback - it breaks AbstractConfigValue.withFallback!
        // Workaround: re-parse my config
        final var configWithFallback = ConfigWithFallback.newInstance(config, CONFIG_PATH,
                AkkaReplicatorConfigValue.values());
        final var fallback =
                ConfigFactory.parseString(configWithFallback.root().render(ConfigRenderOptions.concise()));

        return new DefaultAkkaReplicatorConfig(ConfigFactory.parseMap(specificConfig)
                .withFallback(fallback));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public Duration getGossipInterval() {
        return gossipInterval;
    }

    @Override
    public Duration getNotifySubscribersInterval() {
        return notifySubscribersInterval;
    }

    @Override
    public Config getCompleteConfig() {
        return config;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAkkaReplicatorConfig that = (DefaultAkkaReplicatorConfig) o;
        return Objects.equals(config, that.config) &&
                Objects.equals(name, that.name) &&
                Objects.equals(role, that.role) &&
                Objects.equals(gossipInterval, that.gossipInterval) &&
                Objects.equals(notifySubscribersInterval, that.notifySubscribersInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, name, role, gossipInterval, notifySubscribersInterval);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", role=" + role +
                ", config=" + config +
                ", gossipInterval=" + gossipInterval +
                ", notifySubscribersInterval=" + notifySubscribersInterval +
                "]";
    }
}
