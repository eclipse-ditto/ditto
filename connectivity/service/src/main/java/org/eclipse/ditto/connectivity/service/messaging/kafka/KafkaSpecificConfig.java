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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;

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
     * @throws org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException if the configuration is invalid.
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
     * Creates producer properties for this Kafka specific config.
     * <p>
     * This method will only add configuration to the producer properties if the config {@code isApplicable}
     * and {@code isValid}.
     *
     * @param connection the connection which contains the specific config.
     * @return the producer properties which contain the Kafka specific config.
     */
    Map<String, String> apply(Connection connection);

}
