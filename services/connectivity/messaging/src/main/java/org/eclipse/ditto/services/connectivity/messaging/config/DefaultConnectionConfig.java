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
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.supervision.DefaultSupervisorConfig;
import org.eclipse.ditto.services.base.config.supervision.SupervisorConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class implements {@link ConnectionConfig} for Ditto's Connectivity service.
 */
@Immutable
public final class DefaultConnectionConfig implements ConnectionConfig, Serializable {

    private static final String CONFIG_PATH = "connection";

    private static final long serialVersionUID = -8262639769154675241L;

    private final Duration flushPendingResponsesTimeout;
    private final Duration clientActorAskTimeout;
    private final DefaultSupervisorConfig supervisorConfig;
    private final DefaultSnapshotConfig snapshotConfig;
    private final DefaultMqttConfig mqttConfig;
    private final DefaultKafkaConfig kafkaConfig;

    private DefaultConnectionConfig(final ConfigWithFallback config) {
        flushPendingResponsesTimeout =
                config.getDuration(ConnectionConfigValue.FLUSH_PENDING_RESPONSES_TIMEOUT.getConfigPath());
        clientActorAskTimeout = config.getDuration(ConnectionConfigValue.CLIENT_ACTOR_ASK_TIMEOUT.getConfigPath());
        supervisorConfig = DefaultSupervisorConfig.of(config);
        snapshotConfig = DefaultSnapshotConfig.of(config);
        mqttConfig = DefaultMqttConfig.of(config);
        kafkaConfig = DefaultKafkaConfig.of(config);
    }

    /**
     * Returns {@code DefaultConnectionConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the connection config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultConnectionConfig of(final Config config) {
        return new DefaultConnectionConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConnectionConfigValue.values()));
    }

    @Override
    public Duration getFlushPendingResponsesTimeout() {
        return flushPendingResponsesTimeout;
    }

    @Override
    public Duration getClientActorAskTimeout() {
        return clientActorAskTimeout;
    }

    @Override
    public SupervisorConfig getSupervisorConfig() {
        return supervisorConfig;
    }

    @Override
    public SnapshotConfig getSnapshotConfig() {
        return snapshotConfig;
    }

    @Override
    public MqttConfig getMqttConfig() {
        return mqttConfig;
    }

    @Override
    public KafkaConfig getKafkaConfig() {
        return kafkaConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultConnectionConfig that = (DefaultConnectionConfig) o;
        return Objects.equals(flushPendingResponsesTimeout, that.flushPendingResponsesTimeout) &&
                Objects.equals(clientActorAskTimeout, that.clientActorAskTimeout) &&
                Objects.equals(supervisorConfig, that.supervisorConfig) &&
                Objects.equals(snapshotConfig, that.snapshotConfig) &&
                Objects.equals(mqttConfig, that.mqttConfig) &&
                Objects.equals(kafkaConfig, that.kafkaConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flushPendingResponsesTimeout, clientActorAskTimeout, supervisorConfig, snapshotConfig,
                mqttConfig, kafkaConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "flushPendingResponsesTimeout=" + flushPendingResponsesTimeout +
                ", clientActorAskTimeout=" + clientActorAskTimeout +
                ", supervisorConfig=" + supervisorConfig +
                ", snapshotConfig=" + snapshotConfig +
                ", mqttConfig=" + mqttConfig +
                ", kafkaConfig=" + kafkaConfig +
                "]";
    }

}
