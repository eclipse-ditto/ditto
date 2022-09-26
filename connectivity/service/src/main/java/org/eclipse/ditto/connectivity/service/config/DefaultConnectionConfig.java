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
package org.eclipse.ditto.connectivity.service.config;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.DefaultSupervisorConfig;
import org.eclipse.ditto.base.service.config.supervision.SupervisorConfig;
import org.eclipse.ditto.edge.service.acknowledgements.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultEventConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultSnapshotConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.EventConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.CleanupConfig;

import com.typesafe.config.Config;

/**
 * This class implements {@link ConnectionConfig} for Ditto's Connectivity service.
 */
@Immutable
public final class DefaultConnectionConfig implements ConnectionConfig {

    private static final String CONFIG_PATH = "connection";

    private final Duration clientActorAskTimeout;
    private final int clientActorRestartsBeforeEscalation;
    private final Collection<String> allowedHostnames;
    private final Collection<String> blockedHostnames;
    private final Collection<String> blockedSubnets;
    private final String blockedHostRegex;
    private final SupervisorConfig supervisorConfig;
    private final SnapshotConfig snapshotConfig;
    private final EventConfig eventConfig;
    private final DefaultAcknowledgementConfig acknowledgementConfig;
    private final CleanupConfig cleanupConfig;
    private final Amqp10Config amqp10Config;
    private final Amqp091Config amqp091Config;
    private final MqttConfig mqttConfig;
    private final KafkaConfig kafkaConfig;
    private final HttpPushConfig httpPushConfig;
    private final ActivityCheckConfig activityCheckConfig;
    private final FieldsEncryptionConfig fieldsEncryptionConfig;
    private final Integer maxNumberOfTargets;
    private final Integer maxNumberOfSources;
    private final Duration ackLabelDeclareInterval;
    private final Duration priorityUpdateInterval;
    private final boolean allClientActorsOnOneNode;
    private final Duration shutdownTimeout;

    private DefaultConnectionConfig(final ConfigWithFallback config) {
        clientActorAskTimeout =
                config.getNonNegativeAndNonZeroDurationOrThrow(ConnectionConfigValue.CLIENT_ACTOR_ASK_TIMEOUT);
        clientActorRestartsBeforeEscalation =
                config.getPositiveIntOrThrow(ConnectionConfigValue.CLIENT_ACTOR_RESTARTS_BEFORE_ESCALATION);
        allowedHostnames = fromCommaSeparatedString(config, ConnectionConfigValue.ALLOWED_HOSTNAMES);
        blockedHostnames = fromCommaSeparatedString(config, ConnectionConfigValue.BLOCKED_HOSTNAMES);
        blockedSubnets = fromCommaSeparatedString(config, ConnectionConfigValue.BLOCKED_SUBNETS);
        blockedHostRegex = config.getString(ConnectionConfigValue.BLOCKED_HOST_REGEX.getConfigPath());
        supervisorConfig = DefaultSupervisorConfig.of(config);
        snapshotConfig = DefaultSnapshotConfig.of(config);
        eventConfig = DefaultEventConfig.of(config);
        acknowledgementConfig = DefaultAcknowledgementConfig.of(config);
        cleanupConfig = CleanupConfig.of(config);
        amqp10Config = DefaultAmqp10Config.of(config);
        amqp091Config = DefaultAmqp091Config.of(config);
        mqttConfig = DefaultMqttConfig.of(config);
        kafkaConfig = DefaultKafkaConfig.of(config);
        httpPushConfig = DefaultHttpPushConfig.of(config);
        activityCheckConfig = DefaultActivityCheckConfig.of(config);
        fieldsEncryptionConfig = DefaultFieldsEncryptionConfig.of(config);
        maxNumberOfTargets = config.getNonNegativeIntOrThrow(ConnectionConfigValue.MAX_TARGET_NUMBER);
        maxNumberOfSources = config.getNonNegativeIntOrThrow(ConnectionConfigValue.MAX_SOURCE_NUMBER);
        ackLabelDeclareInterval =
                config.getNonNegativeAndNonZeroDurationOrThrow(ConnectionConfigValue.ACK_LABEL_DECLARE_INTERVAL);
        allClientActorsOnOneNode =
                config.getBoolean(ConnectionConfigValue.ALL_CLIENT_ACTORS_ON_ONE_NODE.getConfigPath());
        priorityUpdateInterval =
                config.getNonNegativeAndNonZeroDurationOrThrow(ConnectionConfigValue.PRIORITY_UPDATE_INTERVAL);
        shutdownTimeout = config.getDuration(ConnectionConfigValue.SHUTDOWN_TIMEOUT.getConfigPath());
    }

    /**
     * Returns {@code DefaultConnectionConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the connection config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultConnectionConfig of(final Config config) {
        return new DefaultConnectionConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConnectionConfigValue.values()));
    }

    private Collection<String> fromCommaSeparatedString(final ConfigWithFallback config,
            final ConnectionConfigValue configValue) {
        final var commaSeparated = config.getString(configValue.getConfigPath());

        return List.of(commaSeparated.split(","));
    }

    @Override
    public Duration getClientActorAskTimeout() {
        return clientActorAskTimeout;
    }

    @Override
    public int getClientActorRestartsBeforeEscalation() {
        return clientActorRestartsBeforeEscalation;
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
    public Collection<String> getBlockedSubnets() {
        return blockedSubnets;
    }

    @Override
    public String getBlockedHostRegex() {
        return blockedHostRegex;
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
    public EventConfig getEventConfig() {
        return eventConfig;
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
    public Duration getAckLabelDeclareInterval() {
        return ackLabelDeclareInterval;
    }

    @Override
    public Duration getPriorityUpdateInterval() {
        return priorityUpdateInterval;
    }

    @Override
    public boolean areAllClientActorsOnOneNode() {
        return allClientActorsOnOneNode;
    }

    @Override
    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    @Override
    public ActivityCheckConfig getActivityCheckConfig() {
        return activityCheckConfig;
    }

    @Override
    public CleanupConfig getCleanupConfig() {
        return cleanupConfig;
    }

    @Override
    public FieldsEncryptionConfig getFieldsEncryptionConfig() {
        return fieldsEncryptionConfig;
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
                Objects.equals(clientActorRestartsBeforeEscalation, that.clientActorRestartsBeforeEscalation) &&
                Objects.equals(allowedHostnames, that.allowedHostnames) &&
                Objects.equals(blockedHostnames, that.blockedHostnames) &&
                Objects.equals(blockedSubnets, that.blockedSubnets) &&
                Objects.equals(blockedHostRegex, that.blockedHostRegex) &&
                Objects.equals(supervisorConfig, that.supervisorConfig) &&
                Objects.equals(snapshotConfig, that.snapshotConfig) &&
                Objects.equals(eventConfig, that.eventConfig) &&
                Objects.equals(acknowledgementConfig, that.acknowledgementConfig) &&
                Objects.equals(cleanupConfig, that.cleanupConfig) &&
                Objects.equals(amqp10Config, that.amqp10Config) &&
                Objects.equals(amqp091Config, that.amqp091Config) &&
                Objects.equals(mqttConfig, that.mqttConfig) &&
                Objects.equals(kafkaConfig, that.kafkaConfig) &&
                Objects.equals(httpPushConfig, that.httpPushConfig) &&
                Objects.equals(activityCheckConfig, that.activityCheckConfig) &&
                Objects.equals(fieldsEncryptionConfig, that.fieldsEncryptionConfig) &&
                Objects.equals(maxNumberOfTargets, that.maxNumberOfTargets) &&
                Objects.equals(maxNumberOfSources, that.maxNumberOfSources) &&
                Objects.equals(ackLabelDeclareInterval, that.ackLabelDeclareInterval) &&
                Objects.equals(priorityUpdateInterval, that.priorityUpdateInterval) &&
                Objects.equals(shutdownTimeout, that.shutdownTimeout) &&
                allClientActorsOnOneNode == that.allClientActorsOnOneNode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientActorAskTimeout, clientActorRestartsBeforeEscalation, allowedHostnames,
                blockedHostnames, blockedSubnets, blockedHostRegex, supervisorConfig, snapshotConfig, eventConfig,
                acknowledgementConfig, cleanupConfig, maxNumberOfTargets, maxNumberOfSources, activityCheckConfig,
                fieldsEncryptionConfig, amqp10Config, amqp091Config, mqttConfig, kafkaConfig, httpPushConfig,
                ackLabelDeclareInterval, priorityUpdateInterval, shutdownTimeout, allClientActorsOnOneNode);
    }

    @Override
    public String toString() {
        return "DefaultConnectionConfig{" +
                "clientActorAskTimeout=" + clientActorAskTimeout +
                ", clientActorRestartsBeforeEscalation=" + clientActorRestartsBeforeEscalation +
                ", allowedHostnames=" + allowedHostnames +
                ", blockedHostnames=" + blockedHostnames +
                ", blockedSubnets=" + blockedSubnets +
                ", blockedHostRegex=" + blockedHostRegex +
                ", supervisorConfig=" + supervisorConfig +
                ", snapshotConfig=" + snapshotConfig +
                ", eventConfig=" + eventConfig +
                ", acknowledgementConfig=" + acknowledgementConfig +
                ", cleanUpConfig=" + cleanupConfig +
                ", amqp10Config=" + amqp10Config +
                ", amqp091Config=" + amqp091Config +
                ", mqttConfig=" + mqttConfig +
                ", kafkaConfig=" + kafkaConfig +
                ", httpPushConfig=" + httpPushConfig +
                ", activityCheckConfig=" + activityCheckConfig +
                ", fieldsEncryptionConfig=" + fieldsEncryptionConfig +
                ", maxNumberOfTargets=" + maxNumberOfTargets +
                ", maxNumberOfSources=" + maxNumberOfSources +
                ", ackLabelDeclareInterval=" + ackLabelDeclareInterval +
                ", priorityUpdateInterval=" + priorityUpdateInterval +
                ", allClientActorsOnOneNode=" + allClientActorsOnOneNode +
                ", shutdownTimeout=" + shutdownTimeout +
                "]";
    }
}
