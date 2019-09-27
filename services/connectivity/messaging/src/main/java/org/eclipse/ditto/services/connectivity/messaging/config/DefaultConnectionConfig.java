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

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.supervision.DefaultSupervisorConfig;
import org.eclipse.ditto.services.base.config.supervision.SupervisorConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultSnapshotConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.SnapshotConfig;

import com.typesafe.config.Config;

/**
 * This class implements {@link ConnectionConfig} for Ditto's Connectivity service.
 */
@Immutable
public final class DefaultConnectionConfig implements ConnectionConfig {

    private static final String CONFIG_PATH = "connection";

    private final Duration clientActorAskTimeout;
    private final SupervisorConfig supervisorConfig;
    private final SnapshotConfig snapshotConfig;
    private final Amqp10Config amqp10Config;
    private final MqttConfig mqttConfig;
    private final KafkaConfig kafkaConfig;
    private final ActivityCheckConfig activityCheckConfig;

    private DefaultConnectionConfig(final ConfigWithFallback config) {
        clientActorAskTimeout = config.getDuration(ConnectionConfigValue.CLIENT_ACTOR_ASK_TIMEOUT.getConfigPath());
        supervisorConfig = DefaultSupervisorConfig.of(config);
        snapshotConfig = DefaultSnapshotConfig.of(config);
        amqp10Config = DefaultAmqp10Config.of(config);
        mqttConfig = DefaultMqttConfig.of(config);
        kafkaConfig = DefaultKafkaConfig.of(config);
        activityCheckConfig = DefaultActivityCheckConfig.of(config);
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
    public Amqp10Config getAmqp10Config() {
        return amqp10Config;
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
    public ActivityCheckConfig getActivityCheckConfig() {
        return activityCheckConfig;
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
        return Objects.equals(clientActorAskTimeout, that.clientActorAskTimeout) &&
                Objects.equals(supervisorConfig, that.supervisorConfig) &&
                Objects.equals(snapshotConfig, that.snapshotConfig) &&
                Objects.equals(amqp10Config, that.amqp10Config) &&
                Objects.equals(mqttConfig, that.mqttConfig) &&
                Objects.equals(activityCheckConfig, that.activityCheckConfig) &&
                Objects.equals(kafkaConfig, that.kafkaConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientActorAskTimeout, supervisorConfig, snapshotConfig, amqp10Config, mqttConfig,
                kafkaConfig, activityCheckConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "clientActorAskTimeout=" + clientActorAskTimeout +
                ", supervisorConfig=" + supervisorConfig +
                ", snapshotConfig=" + snapshotConfig +
                ", amqp10Config=" + amqp10Config +
                ", mqttConfig=" + mqttConfig +
                ", kafkaConfig=" + kafkaConfig +
                ", activityCheckConfig=" + activityCheckConfig +
                "]";
    }

}
