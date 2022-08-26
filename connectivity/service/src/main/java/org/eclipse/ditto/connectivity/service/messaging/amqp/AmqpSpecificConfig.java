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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import static org.apache.qpid.jms.provider.failover.FailoverProviderFactory.FAILOVER_OPTION_PREFIX;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.Amqp10Config;

/**
 * AMQP connection specific config handling that renders a base URI into a JMS connection string.
 */
public final class AmqpSpecificConfig {

    private static final String CLIENT_ID = "jms.clientID";
    private static final String CONNECT_TIMEOUT = "jms.connectTimeout";
    private static final String SEND_TIMEOUT = "jms.sendTimeout";
    private static final String REQUEST_TIMEOUT = "jms.requestTimeout";
    private static final String PREFETCH_POLICY = "jms.prefetchPolicy.all";
    private static final String USERNAME = "jms.username";
    private static final String PASSWORD = "jms.password";
    private static final String SASL_MECHANISMS = "amqp.saslMechanisms";
    private static final String TRUST_ALL = "transport.trustAll";
    private static final String VERIFY_HOST = "transport.verifyHost";

    private final Map<String, String> amqpParameters;
    private final Map<String, String> jmsParameters;
    private final boolean failoverEnabled;
    private final PlainCredentialsSupplier plainCredentialsSupplier;

    private AmqpSpecificConfig(final Map<String, String> amqpParameters,
            final Map<String, String> jmsParameters,
            final boolean failoverEnabled,
            final PlainCredentialsSupplier plainCredentialsSupplier) {

        this.amqpParameters = Collections.unmodifiableMap(new LinkedHashMap<>(amqpParameters));
        this.jmsParameters = Collections.unmodifiableMap(new LinkedHashMap<>(jmsParameters));
        this.failoverEnabled = failoverEnabled;
        this.plainCredentialsSupplier = plainCredentialsSupplier;
    }

    /**
     * Create AMQP specific config with default values.
     *
     * @param clientId the client ID.
     * @param connection the connection.
     * @param defaultConfig the default config values.
     * @param plainCredentialsSupplier supplier of username-password credentials.
     * @return the AMQP specific config.
     */
    public static AmqpSpecificConfig withDefault(final String clientId,
            final Connection connection,
            final Map<String, String> defaultConfig,
            final PlainCredentialsSupplier plainCredentialsSupplier) {

        final var amqpParameters = new LinkedHashMap<>(filterForAmqpParameters(defaultConfig));
        final Optional<UserPasswordCredentials> credentialsOptional = plainCredentialsSupplier.get(connection);
        addSaslMechanisms(amqpParameters, credentialsOptional.isPresent());
        addTransportParameters(amqpParameters, connection);
        addSpecificConfigParameters(amqpParameters, connection, AmqpSpecificConfig::isPermittedAmqpConfig);

        final var jmsParameters = new LinkedHashMap<>(filterForJmsParameters(defaultConfig));
        addParameter(jmsParameters, CLIENT_ID, clientId);
        credentialsOptional.ifPresent(credentials -> addCredentials(jmsParameters, credentials));
        addFailoverParameters(jmsParameters, connection);
        addSpecificConfigParameters(jmsParameters, connection, AmqpSpecificConfig::isPermittedJmsConfig);

        return new AmqpSpecificConfig(amqpParameters, jmsParameters, connection.isFailoverEnabled(),
                plainCredentialsSupplier);
    }

    /**
     * Convert {@link org.eclipse.ditto.connectivity.service.config.Amqp10Config} into a hashmap of config values relevant for a JMS connection string.
     *
     * @param config the Amqp10Config.
     * @return the relevant config values.
     */
    public static Map<String, String> toDefaultConfig(final Amqp10Config config) {
        final LinkedHashMap<String, String> defaultConfig = new LinkedHashMap<>();
        addParameter(defaultConfig, CONNECT_TIMEOUT, config.getGlobalConnectTimeout().toMillis());
        addParameter(defaultConfig, SEND_TIMEOUT, config.getGlobalSendTimeout().toMillis());
        addParameter(defaultConfig, REQUEST_TIMEOUT, config.getGlobalRequestTimeout().toMillis());
        addParameter(defaultConfig, PREFETCH_POLICY, config.getGlobalPrefetchPolicyAllCount());
        return defaultConfig;
    }

    /**
     * Render a base URI into a JMS connection string taking specific config and failover into consideration.
     *
     * @param uri the base URI.
     * @return the rendered connection string.
     */
    public String render(final String uri) {
        final String uriWithoutUserinfo = plainCredentialsSupplier.getUriWithoutUserinfo(uri);
        if (failoverEnabled) {
            final String innerUri = wrapWithFailOver(joinParameters(uriWithoutUserinfo, List.of(amqpParameters)));
            return joinParameters(innerUri, List.of(jmsParameters));
        } else {
            return joinParameters(uriWithoutUserinfo, List.of(amqpParameters, jmsParameters));
        }
    }

    private static String joinParameters(final String prefix, final List<Map<String, String>> maps) {
        return maps.stream()
                .flatMap(map -> map.entrySet().stream())
                .map(entry -> String.format("%s=%s", encode(entry.getKey()), encode(entry.getValue())))
                .collect(Collectors.joining("&", prefix + "?", ""));
    }

    private static boolean isPermittedJmsConfig(final String key) {
        return !key.startsWith("jms.prefetchPolicy") &&
                (key.startsWith("jms") || key.startsWith(FAILOVER_OPTION_PREFIX));
    }

    private static boolean isPermittedAmqpConfig(final String key) {
        return key.startsWith("amqp") || key.startsWith("transport");
    }

    private static void addSpecificConfigParameters(final LinkedHashMap<String, String> parameters,
            final Connection connection, final Predicate<String> keyFilter) {
        connection.getSpecificConfig().forEach((key, value) -> {
            if (keyFilter.test(key)) {
                addParameter(parameters, key, value);
            }
        });
    }

    private static void addSaslMechanisms(final LinkedHashMap<String, String> parameters,
            final boolean hasPlainCredentials) {
        if (hasPlainCredentials) {
            addParameter(parameters, SASL_MECHANISMS, "PLAIN");
        } else {
            addParameter(parameters, SASL_MECHANISMS, "ANONYMOUS");
        }
    }

    private static void addCredentials(final LinkedHashMap<String, String> parameters,
            final UserPasswordCredentials credentials) {

        addParameter(parameters, USERNAME, credentials.getUsername());
        addParameter(parameters, PASSWORD, credentials.getPassword());
    }


    private static void addTransportParameters(final LinkedHashMap<String, String> parameters,
            final Connection connection) {
        if (isSecuredConnection(connection) && !connection.isValidateCertificates()) {
            // these setting can only be applied for amqps connections:
            addParameter(parameters, TRUST_ALL, true);
            addParameter(parameters, VERIFY_HOST, false);
        }
    }

    private static void addParameter(final LinkedHashMap<String, String> parameters, final String key,
            final Object value) {
        parameters.put(key, String.valueOf(value));
    }

    private static String encode(final String string) {
        return UriEncoding.encodeAllButUnreserved(string);
    }

    private static boolean isSecuredConnection(final Connection connection) {
        return ConnectionBasedJmsConnectionFactory.isSecuredConnection(connection);
    }

    private static void addFailoverParameters(final LinkedHashMap<String, String> parameters,
            final Connection connection) {

        if (connection.isFailoverEnabled()) {
            final long fifteenMinutes = Duration.ofMinutes(15L).toMillis();
            // Important: we cannot interrupt connection initiation.
            // These failover parameters ensure qpid client gives up after at most
            // 128 + 256 + 512 + 1024 + 2048 + 4096 = 8064 ms < 10_000 ms = 10 s
            // at the first attempt. The client will retry endlessly after the connection
            // is established with reasonable max reconnect delay until the user terminates
            // the connection manually.
            addParameter(parameters, FAILOVER_OPTION_PREFIX + "startupMaxReconnectAttempts", 5);
            addParameter(parameters, FAILOVER_OPTION_PREFIX + "maxReconnectAttempts", -1);
            addParameter(parameters, FAILOVER_OPTION_PREFIX + "initialReconnectDelay", 128);
            addParameter(parameters, FAILOVER_OPTION_PREFIX + "reconnectDelay", 128);
            addParameter(parameters, FAILOVER_OPTION_PREFIX + "maxReconnectDelay", fifteenMinutes);
            addParameter(parameters, FAILOVER_OPTION_PREFIX + "reconnectBackOffMultiplier", 2);
            addParameter(parameters, FAILOVER_OPTION_PREFIX + "useReconnectBackOff", true);
        }
    }

    private static Map<String, String> filterForAmqpParameters(final Map<String, String> defaultConfig) {
        return defaultConfig.entrySet()
                .stream()
                .filter(entry -> isPermittedAmqpConfig(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, String> filterForJmsParameters(final Map<String, String> defaultConfig) {
        return defaultConfig.entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith("jms"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String wrapWithFailOver(final String uri) {
        return MessageFormat.format("failover:({0})", uri);
    }

}
