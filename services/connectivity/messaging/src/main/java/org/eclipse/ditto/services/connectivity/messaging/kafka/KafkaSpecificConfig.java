/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;

import akka.kafka.ProducerSettings;

/**
 * Interface that allows wrapping the configuration logic of specific Kafka configs.
 */
interface KafkaSpecificConfig {

    /**
     * Checks if the configuration is applicable.
     * This does not include validation.
     *
     * @param connection the connection to check.
     * @return true if the configuration is applicable. It should be validated afterwards.
     */
    boolean isApplicable(Connection connection);

    /**
     * Validates the given connection against the config.
     * Assumes that the connection {@code isApplicable}.
     *
     * @param connection the connection to validate.
     * @param dittoHeaders headers that were sent with the connection.
     * @throws org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException if the configuration is invalid.
     */
    void validateOrThrow(Connection connection, DittoHeaders dittoHeaders);

    /**
     * Checks if the given connection configuration contains valid values for its specific config.
     *
     * @param connection the connection to validate.
     * @return true if the connection is valid.
     */
    boolean isValid(Connection connection);

    /**
     * Apply this Kafka config to the given {@code producerSettings}.
     *
     * This method will only add configuration to the {@code producerSettings} if the config {@code isApplicable}
     * and {@code isValid}.
     *
     * @param producerSettings the producer settings to which the Kafka config is appended.
     * @param connection the connection which contains the specific config.
     * @return the {@code producerSettings} enhanced with new configuration provided by the Kafka config.
     */
    ProducerSettings<String, String> apply(ProducerSettings<String, String> producerSettings, Connection connection);

}
