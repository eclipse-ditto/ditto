/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.common.config.SaslConfigs;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;

import akka.kafka.ProducerSettings;

/**
 * Adds the correct authentication for the chosen SASL mechanism in the specific config of the connection.
 */
public final class KafkaAuthenticationSpecificConfig implements KafkaSpecificConfig {

    private static final String SPECIFIC_CONFIG_SASL_MECHANISM_KEY = "saslMechanism";

    @SuppressWarnings("squid:S2068")
    private static final String JAAS_CONFIG_TEMPLATE = "%s required username=\"%s\" password=\"%s\";";
    private static final String PLAIN_SASL_MECHANISM = "PLAIN";
    private static final String DEFAULT_SASL_MECHANISM = PLAIN_SASL_MECHANISM;
    private static final Map<String, String> SASL_MECHANISMS_WITH_LOGIN_MODULE = new HashMap<>();

    private static KafkaAuthenticationSpecificConfig instance;

    private KafkaAuthenticationSpecificConfig() {
        SASL_MECHANISMS_WITH_LOGIN_MODULE.put(PLAIN_SASL_MECHANISM,
                "org.apache.kafka.common.security.plain.PlainLoginModule");
        SASL_MECHANISMS_WITH_LOGIN_MODULE.put("SCRAM-SHA-256",
                "org.apache.kafka.common.security.scram.ScramLoginModule");
        SASL_MECHANISMS_WITH_LOGIN_MODULE.put("SCRAM-SHA-512",
                "org.apache.kafka.common.security.scram.ScramLoginModule");
    }

    public static KafkaAuthenticationSpecificConfig getInstance() {
        if (null == instance) {
            instance = new KafkaAuthenticationSpecificConfig();
        }
        return instance;
    }

    @Override
    public boolean isApplicable(final Connection connection) {
        return connection.getUsername().isPresent() && connection.getPassword().isPresent();
    }

    @Override
    public void validateOrThrow(final Connection connection, final DittoHeaders dittoHeaders) {
        if (!isValid(connection)) {
            final String message = MessageFormat.format(
                    "The connection configuration contains an invalid value for SASL mechanisms: <{0}>. Allowed " +
                            "mechanisms are: <{1}>", getSaslMechanismOrDefault(connection),
                    SASL_MECHANISMS_WITH_LOGIN_MODULE.keySet());
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Override
    public boolean isValid(final Connection connection) {
        return containsValidSaslMechanismConfiguration(connection);
    }

    private boolean containsValidSaslMechanismConfiguration(final Connection connection) {
        final String mechanism = getSaslMechanismOrDefault(connection);
        return SASL_MECHANISMS_WITH_LOGIN_MODULE.containsKey(mechanism.toUpperCase());
    }

    @Override
    public ProducerSettings<String, String> apply(final ProducerSettings<String, String> producerSettings,
            final Connection connection) {
        final Optional<String> username = connection.getUsername();
        final Optional<String> password = connection.getPassword();
        // chose to not use isApplicable() but directly check username and password since we need to Optional#get them.
        if (isValid(connection) && username.isPresent() && password.isPresent()) {
            final String saslMechanism = getSaslMechanismOrDefault(connection).toUpperCase();
            final String loginModule = getLoginModuleForSaslMechanism(saslMechanism);
            final String jaasConfig = getJaasConfig(loginModule, username.get(), password.get());

            return producerSettings.withProperty(SaslConfigs.SASL_MECHANISM, saslMechanism)
                    .withProperty(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
        }

        return producerSettings;
    }

    private String getJaasConfig(final String loginModule, final String username, final String password) {
        return String.format(JAAS_CONFIG_TEMPLATE, loginModule, username, password);
    }

    private String getSaslMechanismOrDefault(final Connection connection) {
        return connection.getSpecificConfig().getOrDefault(SPECIFIC_CONFIG_SASL_MECHANISM_KEY, DEFAULT_SASL_MECHANISM);
    }

    private String getLoginModuleForSaslMechanism(final String saslMechanism) {
        return SASL_MECHANISMS_WITH_LOGIN_MODULE.get(saslMechanism);
    }

}
