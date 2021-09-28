/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.Optional;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;

/**
 * Checks if certain parts of a modified {@link ConnectivityConfig} have changed in comparison with a given {@link ConnectivityConfig}.
 */
final class ConfigModifiedChecker {

    private ConfigModifiedChecker() {}

    /**
     * Checks if the config relevant for an {@code InboundMappingProcessor} has changed.
     *
     * @param connection the connection
     * @param currentConfig the current config
     * @param modifiedConfig the modified config
     * @return whether the config relevant for an {@code InboundMappingProcessor} has changed
     */
    static boolean hasInboundMapperConfigChanged(final Connection connection, final ConnectivityConfig currentConfig,
            final ConnectivityConfig modifiedConfig) {

        final var currentMapperLimitsConfig = currentConfig.getMappingConfig().getMapperLimitsConfig();
        final var modifiedMapperLimitsConfig = modifiedConfig.getMappingConfig().getMapperLimitsConfig();

        return currentMapperLimitsConfig.getMaxMappedInboundMessages() !=
                modifiedMapperLimitsConfig.getMaxMappedInboundMessages()
                || currentMapperLimitsConfig.getMaxSourceMappers() != modifiedMapperLimitsConfig.getMaxSourceMappers()
                || (connection.getConnectionType() == ConnectionType.KAFKA && hasThrottlingConfigChanged(
                modifiedConfig.getConnectionConfig().getKafkaConfig().getConsumerConfig().getThrottlingConfig(),
                currentConfig.getConnectionConfig().getKafkaConfig().getConsumerConfig().getThrottlingConfig()))
                || (connection.getConnectionType() == ConnectionType.AMQP_10 && hasThrottlingConfigChanged(
                modifiedConfig.getConnectionConfig().getAmqp10Config().getConsumerConfig().getThrottlingConfig(),
                currentConfig.getConnectionConfig().getAmqp10Config().getConsumerConfig().getThrottlingConfig()));
    }

    /**
     * Checks if the config relevant for an {@code OutboundMappingProcessor} has changed.
     *
     * @param currentConfig the current config
     * @param modifiedConfig the modified config
     * @return whether the config relevant for an {@code OutboundMappingProcessor} has changed
     */
    static boolean hasOutboundMapperConfigChanged(final ConnectivityConfig currentConfig,
            final ConnectivityConfig modifiedConfig) {
        final var currentMapperLimitsConfig = currentConfig.getMappingConfig().getMapperLimitsConfig();
        final var modifiedMapperLimitsConfig = modifiedConfig.getMappingConfig().getMapperLimitsConfig();
        return currentMapperLimitsConfig.getMaxMappedOutboundMessages() !=
                modifiedMapperLimitsConfig.getMaxMappedOutboundMessages()
                || currentMapperLimitsConfig.getMaxTargetMappers() != modifiedMapperLimitsConfig.getMaxTargetMappers();
    }

    /**
     * Returns the modified throttling config for the given connection type if it changed compared to the current
     * config or an empty Optional if it did not change or the connection type has no throttling config defined.
     *
     * @param connection the connection
     * @param currentConfig the current config
     * @param modifiedConfig the modified config
     * @return returns the modified throttling config if it changed compared to the current config, empty Optional
     * otherwise.
     */
    static Optional<ThrottlingConfig> getModifiedThrottlingConfig(final Connection connection,
            final ConnectivityConfig currentConfig, final ConnectivityConfig modifiedConfig) {
        final ConnectionType connectionType = connection.getConnectionType();
        return getThrottlingConfig(connectionType, currentConfig)
                .flatMap(currentThrottlingConfig -> getThrottlingConfig(connectionType, modifiedConfig).map(
                        modifiedThrottlingConfig ->
                                hasThrottlingConfigChanged(currentThrottlingConfig, modifiedThrottlingConfig) ?
                                        modifiedThrottlingConfig : null));
    }

    private static Optional<ThrottlingConfig> getThrottlingConfig(final ConnectionType connectionType,
            final ConnectivityConfig config) {
        switch (connectionType) {
            case AMQP_10:
                return Optional.of(
                        config.getConnectionConfig().getAmqp10Config().getConsumerConfig().getThrottlingConfig());
            case KAFKA:
                return Optional.of(
                        config.getConnectionConfig().getKafkaConfig().getConsumerConfig().getThrottlingConfig());
            default:
                return Optional.empty();
        }
    }

    private static boolean hasThrottlingConfigChanged(final ThrottlingConfig currentThrottlingConfig,
            final ThrottlingConfig modifiedThrottlingConfig) {
        return !currentThrottlingConfig.getInterval().equals(modifiedThrottlingConfig.getInterval())
                || currentThrottlingConfig.getLimit() != modifiedThrottlingConfig.getLimit();
    }

}
