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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.supervision.DefaultSupervisorConfig;
import org.eclipse.ditto.services.base.config.supervision.SupervisorConfig;
import org.eclipse.ditto.services.models.acks.config.DefaultAcknowledgementConfig;
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
    private final Collection<String> allowedHostnames;
    private final Collection<String> blockedHostnames;
    private final SupervisorConfig supervisorConfig;
    private final SnapshotConfig snapshotConfig;
    private final DefaultAcknowledgementConfig acknowledgementConfig;
    private final Amqp10Config amqp10Config;
    private final Amqp091Config amqp091Config;
    private final MqttConfig mqttConfig;
    private final KafkaConfig kafkaConfig;
    private final HttpPushConfig httpPushConfig;
    private final ActivityCheckConfig activityCheckConfig;
    private final Integer maxNumberOfTargets;
    private final Integer maxNumberOfSources;

    private DefaultConnectionConfig(final ConfigWithFallback config) {
        clientActorAskTimeout = config.getDuration(ConnectionConfigValue.CLIENT_ACTOR_ASK_TIMEOUT.getConfigPath());
        allowedHostnames = fromCommaSeparatedString(config, ConnectionConfigValue.ALLOWED_HOSTNAMES);
        blockedHostnames = fromCommaSeparatedString(config, ConnectionConfigValue.BLOCKED_HOSTNAMES);
        supervisorConfig = DefaultSupervisorConfig.of(config);
        snapshotConfig = DefaultSnapshotConfig.of(config);
        acknowledgementConfig = DefaultAcknowledgementConfig.of(config);
        amqp10Config = DefaultAmqp10Config.of(config);
        amqp091Config = DefaultAmqp091Config.of(config);
        mqttConfig = DefaultMqttConfig.of(config);
        kafkaConfig = DefaultKafkaConfig.of(config);
        httpPushConfig = DefaultHttpPushConfig.of(config);
        activityCheckConfig = DefaultActivityCheckConfig.of(config);
        maxNumberOfTargets = config.getInt(ConnectionConfigValue.MAX_TARGET_NUMBER.getConfigPath());
        maxNumberOfSources = config.getInt(ConnectionConfigValue.MAX_SOURCE_NUMBER.getConfigPath());
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

    private Collection<String> fromCommaSeparatedString(final ConfigWithFallback config,
            final ConnectionConfigValue configValue) {
        final String commaSeparated = config.getString(configValue.getConfigPath());
        return Collections.unmodifiableCollection(Arrays.asList(commaSeparated.split(",")));
    }

    @Override
    public Duration getClientActorAskTimeout() {
        return clientActorAskTimeout;
    }

    @Override
    public Collection<String> getAllowedHostnames() {
        return allowedHostnames;
    }

    @Override
    public Collection<String> getBlockedHostnames() {
        return blockedHostnames;
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
    public Integer getMaxNumberOfTargets() {
        return maxNumberOfTargets;
    }

    @Override
    public Integer getMaxNumberOfSources() {
        return maxNumberOfSources;
    }

    @Override
    public DefaultAcknowledgementConfig getAcknowledgementConfig() {
        return acknowledgementConfig;
    }

    @Override
    public Amqp10Config getAmqp10Config() {
        return amqp10Config;
    }

    @Override
    public Amqp091Config getAmqp091Config() {
        return amqp091Config;
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
    public HttpPushConfig getHttpPushConfig() {
        return httpPushConfig;
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
                Objects.equals(allowedHostnames, that.allowedHostnames) &&
                Objects.equals(blockedHostnames, that.blockedHostnames) &&
                Objects.equals(supervisorConfig, that.supervisorConfig) &&
                Objects.equals(snapshotConfig, that.snapshotConfig) &&
                Objects.equals(acknowledgementConfig, that.acknowledgementConfig) &&
                Objects.equals(amqp10Config, that.amqp10Config) &&
                Objects.equals(amqp091Config, that.amqp091Config) &&
                Objects.equals(mqttConfig, that.mqttConfig) &&
                Objects.equals(kafkaConfig, that.kafkaConfig) &&
                Objects.equals(httpPushConfig, that.httpPushConfig) &&
                Objects.equals(activityCheckConfig, that.activityCheckConfig) &&
                Objects.equals(maxNumberOfTargets, that.maxNumberOfTargets) &&
                Objects.equals(maxNumberOfSources, that.maxNumberOfSources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientActorAskTimeout, allowedHostnames, blockedHostnames, supervisorConfig, snapshotConfig,
                acknowledgementConfig, amqp10Config, amqp091Config, mqttConfig, kafkaConfig, httpPushConfig,
                activityCheckConfig, maxNumberOfTargets, maxNumberOfSources);
    }

    @Override
    public String toString() {
        return "DefaultConnectionConfig{" +
                "clientActorAskTimeout=" + clientActorAskTimeout +
                ", allowedHostnames=" + allowedHostnames +
                ", blockedHostnames=" + blockedHostnames +
                ", supervisorConfig=" + supervisorConfig +
                ", snapshotConfig=" + snapshotConfig +
                ", acknowledgementConfig=" + acknowledgementConfig +
                ", amqp10Config=" + amqp10Config +
                ", amqp091Config=" + amqp091Config +
                ", mqttConfig=" + mqttConfig +
                ", kafkaConfig=" + kafkaConfig +
                ", httpPushConfig=" + httpPushConfig +
                ", activityCheckConfig=" + activityCheckConfig +
                ", maxNumberOfTargets=" + maxNumberOfTargets +
                ", maxNumberOfSources=" + maxNumberOfSources +
                '}';
    }
}
