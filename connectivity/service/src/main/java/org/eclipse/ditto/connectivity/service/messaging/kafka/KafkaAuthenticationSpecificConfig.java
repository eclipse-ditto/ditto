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

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.kafka.common.config.SaslConfigs;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;

/**
 * Adds the correct authentication for the chosen SASL mechanism in the specific config of the connection.
 */
final class KafkaAuthenticationSpecificConfig implements KafkaSpecificConfig {

    private static final String SPECIFIC_CONFIG_SASL_MECHANISM_KEY = "saslMechanism";

    @SuppressWarnings("squid:S2068")
    private static final String JAAS_CONFIG_TEMPLATE = "%s required username=\"%s\" password=\"%s\";";
    private static final String PLAIN_SASL_MECHANISM = "PLAIN";
    private static final String DEFAULT_SASL_MECHANISM = PLAIN_SASL_MECHANISM;
    private static final Map<String, String> SASL_MECHANISMS_WITH_LOGIN_MODULE = new HashMap<>();

    @Nullable private static KafkaAuthenticationSpecificConfig instance;

    private KafkaAuthenticationSpecificConfig() {
        SASL_MECHANISMS_WITH_LOGIN_MODULE.put(PLAIN_SASL_MECHANISM,
                "org.apache.kafka.common.security.plain.PlainLoginModule");
        SASL_MECHANISMS_WITH_LOGIN_MODULE.put("SCRAM-SHA-256",
                "org.apache.kafka.common.security.scram.ScramLoginModule");
        SASL_MECHANISMS_WITH_LOGIN_MODULE.put("SCRAM-SHA-512",
                "org.apache.kafka.common.security.scram.ScramLoginModule");
    }

    public static KafkaAuthenticationSpecificConfig getInstance() {
        KafkaAuthenticationSpecificConfig result = instance;
        if (null == result) {
            result = new KafkaAuthenticationSpecificConfig();
            instance = result;
        }
        return result;
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

    private static boolean containsValidSaslMechanismConfiguration(final Connection connection) {
        final String mechanism = getSaslMechanismOrDefault(connection);
        return SASL_MECHANISMS_WITH_LOGIN_MODULE.containsKey(mechanism.toUpperCase());
    }

    @Override
    public Map<String, String> apply(final Connection connection) {

        final Optional<String> username = connection.getUsername();
        final Optional<String> password = connection.getPassword();
        // chose to not use isApplicable() but directly check username and password since we need to Optional#get them.
        if (isValid(connection) && username.isPresent() && password.isPresent()) {
            final String saslMechanism = getSaslMechanismOrDefault(connection).toUpperCase();
            final String loginModule = getLoginModuleForSaslMechanism(saslMechanism);
            final String jaasConfig = getJaasConfig(loginModule, username.get(), password.get());

            return Map.of(SaslConfigs.SASL_MECHANISM, saslMechanism,
                    SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
        }
        return Collections.emptyMap();
    }

    private static String getJaasConfig(final String loginModule, final String username, final String password) {
        return String.format(JAAS_CONFIG_TEMPLATE, loginModule, username, password);
    }

    private static String getSaslMechanismOrDefault(final Connection connection) {
        return connection.getSpecificConfig().getOrDefault(SPECIFIC_CONFIG_SASL_MECHANISM_KEY, DEFAULT_SASL_MECHANISM);
    }

    private static String getLoginModuleForSaslMechanism(final String saslMechanism) {
        return SASL_MECHANISMS_WITH_LOGIN_MODULE.get(saslMechanism);
    }

}
