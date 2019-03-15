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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.util.ConnectionConfigReader;
import org.junit.Test;

import com.typesafe.config.Config;

import akka.kafka.ProducerSettings;

/**
 * Unit test for {@link KafkaAuthenticationSpecificConfig}.
 */
public class KafkaAuthenticationSpecificConfigTest {
    private static final DittoHeaders HEADERS = DittoHeaders.empty();
    private static final String DEFAULT_HOST = "localhost:1883";
    private static final Map<String, String> DEFAULT_SPECIFIC_CONFIG = new HashMap<>();
    private static final Config CONFIG = ConnectionConfigReader.fromRawConfig(TestConstants.CONFIG).kafka().internalProducerSettings();
    private static final ProducerSettings<String, String> DEFAULT_PRODUCER_SETTINGS = ProducerSettings.create(CONFIG, new StringSerializer(), new StringSerializer());

    static {
        DEFAULT_SPECIFIC_CONFIG.put("bootstrapServers", DEFAULT_HOST);
    }

    private static final String KNOWN_USER = "knownUser";
    private static final String KNOWN_PASSWORD = "knownPassword";

    private final KafkaAuthenticationSpecificConfig kafkaAuthenticationSpecificConfig = KafkaAuthenticationSpecificConfig.getInstance();

    @Test
    public void shouldBeApplicableIfCredentialsExist() {
        shouldBeApplicable(defaultConnection());
    }

    @Test
    public void shouldNotBeApplicableIfCredentialsAreMissing() {
        shouldNotBeApplicable(connectionWithUsernameAndPassword(null, null));
    }

    private void shouldBeApplicable(final Connection connection) {
        assertThat(kafkaAuthenticationSpecificConfig.isApplicable(connection)).isTrue();
    }

    private void shouldNotBeApplicable(final Connection connection) {
        assertThat(kafkaAuthenticationSpecificConfig.isApplicable(connection)).isFalse();
    }

    @Test
    public void shouldBeValidIfContainsNoSaslMechanism() {
        shouldBeValid(connectionWithSaslMechanism(null));
    }

    @Test
    public void shouldBeValidIfContainsSupportedSaslMechanism() {
        shouldBeValid(connectionWithSaslMechanism("PLAIN"));
        shouldBeValid(connectionWithSaslMechanism("SCRAM-SHA-256"));
        shouldBeValid(connectionWithSaslMechanism("SCRAM-SHA-512"));
    }

    @Test
    public void shouldBeInvalidIfContainsUnsupportedSaslMechanism() {
        shouldNotBeValid(connectionWithSaslMechanism("OAUTHBEARER"));
        shouldNotBeValid(connectionWithSaslMechanism("GSSAPI"));
        shouldNotBeValid(connectionWithSaslMechanism("anything else"));
    }

    private void shouldBeValid(final Connection connection) {
        assertThat(kafkaAuthenticationSpecificConfig.isValid(connection)).isTrue();
    }

    private void shouldNotBeValid(final Connection connection) {
        assertThat(kafkaAuthenticationSpecificConfig.isValid(connection)).isFalse();
    }

    @Test
    public void shouldValidateIfContainsNoSaslMechanism() {
        shouldValidate(connectionWithSaslMechanism(null));
    }

    @Test
    public void shouldValidateIfContainsSupportedSaslMechanism() {
        shouldValidate(connectionWithSaslMechanism("PLAIN"));
        shouldValidate(connectionWithSaslMechanism("SCRAM-SHA-256"));
        shouldValidate(connectionWithSaslMechanism("SCRAM-SHA-512"));
    }

    @Test
    public void shouldThrowOnValidationIfContainsUnsupportedSaslMechanism() {
        shouldNotValidate(connectionWithSaslMechanism("OAUTHBEARER"));
        shouldNotValidate(connectionWithSaslMechanism("GSSAPI"));
        shouldNotValidate(connectionWithSaslMechanism("anything else"));
    }

    private void shouldValidate(final Connection connection) {
        kafkaAuthenticationSpecificConfig.validateOrThrow(connection, HEADERS);
    }

    private void shouldNotValidate(final Connection connection) {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> kafkaAuthenticationSpecificConfig.validateOrThrow(connection, HEADERS));
    }

    @Test
    public void shouldNotAddSaslConfigurationIfCredentialsAreMissingg() {
        shouldNotContainSaslMechanism(connectionWithUsernameAndPassword(null, null));
    }
    @Test
    public void shouldAddDefaultSaslConfigurationIfSaslMechanismIsMissing() {
        shouldContainPlainSaslMechanism(connectionWithSaslMechanism(null), "PLAIN");
    }

    @Test
    public void shouldNotAddSaslConfigurationIfSaslMechanismIsUnsupported() {
        shouldNotContainSaslMechanism(connectionWithSaslMechanism("OAUTHBEARER"));
        shouldNotContainSaslMechanism(connectionWithSaslMechanism("GSSAPI"));
        shouldNotContainSaslMechanism(connectionWithSaslMechanism("anything else"));
    }

    @Test
    public void shouldAddSaslConfigurationIfSaslMechanismIsSupported() {
        shouldContainPlainSaslMechanism(connectionWithSaslMechanism("PLAIN"), "PLAIN");
        shouldContainScramSaslMechanism(connectionWithSaslMechanism("SCRAM-SHA-256"), "SCRAM-SHA-256");
        shouldContainScramSaslMechanism(connectionWithSaslMechanism("SCRAM-SHA-512"), "SCRAM-SHA-512");

    }

    private void shouldNotContainSaslMechanism(final Connection connection) {
        final ProducerSettings<String, String> settings = kafkaAuthenticationSpecificConfig.apply(DEFAULT_PRODUCER_SETTINGS, connection);
        assertThat(settings.properties().get(SaslConfigs.SASL_MECHANISM).isDefined()).isFalse();
        assertThat(settings.properties().get(SaslConfigs.SASL_JAAS_CONFIG).isDefined()).isFalse();
    }

    private void shouldContainPlainSaslMechanism(final Connection connection, final String mechanism) {

        final ProducerSettings<String, String> settings = kafkaAuthenticationSpecificConfig.apply(DEFAULT_PRODUCER_SETTINGS, connection);
        assertThat(settings.properties().get(SaslConfigs.SASL_MECHANISM).get()).isEqualTo(mechanism);
        assertThat(settings.properties().get(SaslConfigs.SASL_JAAS_CONFIG).get()).isEqualTo(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + KNOWN_USER + "\" password=\"" + KNOWN_PASSWORD + "\""
        );
    }

    private void shouldContainScramSaslMechanism(final Connection connection, final String mechanism) {

        final ProducerSettings<String, String> settings = kafkaAuthenticationSpecificConfig.apply(DEFAULT_PRODUCER_SETTINGS, connection);
        assertThat(settings.properties().get(SaslConfigs.SASL_MECHANISM).get()).isEqualTo(mechanism);
        assertThat(settings.properties().get(SaslConfigs.SASL_JAAS_CONFIG).get()).isEqualTo(
                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"" + KNOWN_USER + "\" password=\"" + KNOWN_PASSWORD + "\""
        );
    }

    private static Connection defaultConnection() {
        return connection(KNOWN_USER, KNOWN_PASSWORD, DEFAULT_SPECIFIC_CONFIG);
    }

    private static Connection connectionWithSaslMechanism(@Nullable final String saslMechanism) {
        return connection(KNOWN_USER, KNOWN_PASSWORD, specificConfigWithSaslMechanism(saslMechanism));
    }

    private static Connection connectionWithUsernameAndPassword(@Nullable final String username, @Nullable final String password) {
        return connection(username, password, DEFAULT_SPECIFIC_CONFIG);
    }

    private static Connection connection(@Nullable final String username, @Nullable final String password, final Map<String, String> specificConfig){
        final String uri = uriWithUserAndPassword(username, password);
        return ConnectivityModelFactory.newConnectionBuilder("kafka", ConnectionType.KAFKA,
                ConnectivityStatus.OPEN, uri)
                .targets(singletonList(
                        org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newTarget("target", AUTHORIZATION_CONTEXT, null, 1, Topic.LIVE_EVENTS)))
                .specificConfig(specificConfig)
                .build();
    }

    private static String uriWithUserAndPassword(@Nullable final String username, @Nullable final String password) {
        final boolean usernameAndPasswordMissing = username == null && password == null;
        final String userPasswordSeparator = usernameAndPasswordMissing ? "" : ":";
        final String credentialsHostSeparator = usernameAndPasswordMissing ? "" : "@";
        final String uriTemplate = "tcp://%s%s%s%s%s";
        return String.format(uriTemplate,
                null == username ? "" : username,
                userPasswordSeparator,
                null == password ? "" : password,
                credentialsHostSeparator,
                DEFAULT_HOST);
    }

    private static Map<String, String> specificConfigWithSaslMechanism(@Nullable final String saslMechanism) {
        if (null == saslMechanism) {
            return DEFAULT_SPECIFIC_CONFIG;
        }

        final Map<String, String> configWithSasl = new HashMap<>(DEFAULT_SPECIFIC_CONFIG);
        configWithSasl.put("saslMechanism", saslMechanism);
        return configWithSasl;
    }
}