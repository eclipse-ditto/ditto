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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.config.KafkaConfig;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;

import akka.kafka.ProducerSettings;

/**
 * Unit test for {@link KafkaAuthenticationSpecificConfig}.
 */
@SuppressWarnings("squid:S2068")
public final class KafkaAuthenticationSpecificConfigTest {

    private static final String KNOWN_PLAIN_SASL_MECHANISM = "PLAIN";
    private static final String KNOWN_SHA256_SASL_MECHANISM = "SCRAM-SHA-256";
    private static final String KNOWN_SHA512_SASL_MECHANISM = "SCRAM-SHA-512";
    private static final String KNOWN_UNSUPPORTED_GSSAPI_SASL_MECHANISM = "GSSAPI";
    private static final String KNOWN_UNSUPPORTED_OAUTHBEARER_SASL_MECHANISM = "OAUTHBEARER";
    private static final String UNKNOWN_SASL_MECHANISM = "ANYTHING-ELSE";
    private static final DittoHeaders HEADERS = DittoHeaders.empty();
    private static final String DEFAULT_HOST = "localhost:1883";
    private static final String KNOWN_USER = "knownUser";
    private static final String KNOWN_PASSWORD = "knownPassword";

    private static Map<String, String> defaultSpecificConfig;
    private static ProducerSettings<String, String> defaultProducerSettings;

    private KafkaAuthenticationSpecificConfig underTest;

    @BeforeClass
    public static void initTestFixture() {
        defaultSpecificConfig = new HashMap<>();
        defaultSpecificConfig.put("bootstrapServers", DEFAULT_HOST);

        final KafkaConfig kafkaConfig = TestConstants.CONNECTION_CONFIG.getKafkaConfig();
        final Config internalProducerConfig = kafkaConfig.getInternalProducerConfig();
        final Serializer<String> stringSerializer = new StringSerializer();
        defaultProducerSettings = ProducerSettings.create(internalProducerConfig, stringSerializer, stringSerializer);
    }

    @Before
    public void setUp() {
        underTest = KafkaAuthenticationSpecificConfig.getInstance();
    }

    @Test
    public void shouldBeApplicableIfCredentialsExist() {
        final Connection defaultConnection = getConnection(KNOWN_USER, KNOWN_PASSWORD, defaultSpecificConfig);

        assertThat(underTest.isApplicable(defaultConnection)).isTrue();
    }

    @Test
    public void shouldNotBeApplicableIfCredentialsAreMissing() {
        final Connection connection = getConnectionWithoutUsernameAndPassword();

        assertThat(underTest.isApplicable(connection)).isFalse();
    }

    @Test
    public void shouldBeValidIfContainsNoSaslMechanism() {
        assertThat(underTest.isValid(getConnectionWithSaslMechanism(null))).isTrue();
    }

    @Test
    public void shouldBeValidIfContainsSupportedSaslMechanism() {
        assertThat(underTest.isValid(getConnectionWithSaslMechanism(KNOWN_PLAIN_SASL_MECHANISM))).isTrue();
        assertThat(underTest.isValid(getConnectionWithSaslMechanism(KNOWN_SHA256_SASL_MECHANISM))).isTrue();
        assertThat(underTest.isValid(getConnectionWithSaslMechanism(KNOWN_SHA512_SASL_MECHANISM))).isTrue();
    }

    @Test
    public void shouldBeInvalidIfContainsUnsupportedSaslMechanism() {
        assertThat(underTest.isValid(
                getConnectionWithSaslMechanism(KNOWN_UNSUPPORTED_OAUTHBEARER_SASL_MECHANISM))).isFalse();
        assertThat(
                underTest.isValid(getConnectionWithSaslMechanism(KNOWN_UNSUPPORTED_GSSAPI_SASL_MECHANISM))).isFalse();
        assertThat(underTest.isValid(getConnectionWithSaslMechanism(UNKNOWN_SASL_MECHANISM))).isFalse();
    }

    @Test
    public void shouldValidateIfContainsNoSaslMechanism() {
        underTest.validateOrThrow(getConnectionWithSaslMechanism(null), HEADERS);
    }

    @Test
    public void shouldValidateIfContainsSupportedSaslMechanism() {
        underTest.validateOrThrow(getConnectionWithSaslMechanism(KNOWN_PLAIN_SASL_MECHANISM), HEADERS);
        underTest.validateOrThrow(getConnectionWithSaslMechanism(KNOWN_SHA256_SASL_MECHANISM), HEADERS);
        underTest.validateOrThrow(getConnectionWithSaslMechanism(KNOWN_SHA512_SASL_MECHANISM), HEADERS);
    }

    @Test
    public void shouldThrowOnValidationIfContainsUnsupportedSaslMechanism() {
        shouldNotValidate(getConnectionWithSaslMechanism(KNOWN_UNSUPPORTED_OAUTHBEARER_SASL_MECHANISM));
        shouldNotValidate(getConnectionWithSaslMechanism(KNOWN_UNSUPPORTED_GSSAPI_SASL_MECHANISM));
        shouldNotValidate(getConnectionWithSaslMechanism(UNKNOWN_SASL_MECHANISM));
    }

    private void shouldNotValidate(final Connection connection) {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validateOrThrow(connection, HEADERS));
    }

    @Test
    public void shouldNotAddSaslConfigurationIfCredentialsAreMissingg() {
        shouldNotContainSaslMechanism(getConnectionWithoutUsernameAndPassword());
    }

    @Test
    public void shouldAddDefaultSaslConfigurationIfSaslMechanismIsMissing() {
        shouldContainPlainSaslMechanism(getConnectionWithSaslMechanism(null));
    }

    @Test
    public void shouldNotAddSaslConfigurationIfSaslMechanismIsUnsupported() {
        shouldNotContainSaslMechanism(getConnectionWithSaslMechanism(KNOWN_UNSUPPORTED_OAUTHBEARER_SASL_MECHANISM));
        shouldNotContainSaslMechanism(getConnectionWithSaslMechanism(KNOWN_UNSUPPORTED_GSSAPI_SASL_MECHANISM));
        shouldNotContainSaslMechanism(getConnectionWithSaslMechanism(UNKNOWN_SASL_MECHANISM));
    }

    @Test
    public void shouldAddSaslConfigurationIfSaslMechanismIsSupported() {
        shouldContainPlainSaslMechanism(getConnectionWithSaslMechanism(KNOWN_PLAIN_SASL_MECHANISM));
        shouldContainScramSaslMechanism(getConnectionWithSaslMechanism(KNOWN_SHA256_SASL_MECHANISM),
                KNOWN_SHA256_SASL_MECHANISM);
        shouldContainScramSaslMechanism(getConnectionWithSaslMechanism(KNOWN_SHA512_SASL_MECHANISM),
                KNOWN_SHA512_SASL_MECHANISM);

    }

    private void shouldNotContainSaslMechanism(final Connection connection) {
        final ProducerSettings<String, String> settings = underTest.apply(defaultProducerSettings, connection);

        assertThat(settings.properties().get(SaslConfigs.SASL_MECHANISM).isDefined()).isFalse();
        assertThat(settings.properties().get(SaslConfigs.SASL_JAAS_CONFIG).isDefined()).isFalse();
    }

    private void shouldContainPlainSaslMechanism(final Connection connection) {
        final ProducerSettings<String, String> settings = underTest.apply(defaultProducerSettings, connection);

        assertThat(settings.properties().get(SaslConfigs.SASL_MECHANISM).get()).isEqualTo(KNOWN_PLAIN_SASL_MECHANISM);
        assertThat(settings.properties().get(SaslConfigs.SASL_JAAS_CONFIG).get()).isEqualTo(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + KNOWN_USER +
                        "\" password=\"" + KNOWN_PASSWORD + "\";"
        );
    }

    private void shouldContainScramSaslMechanism(final Connection connection, final String mechanism) {
        final ProducerSettings<String, String> settings = underTest.apply(defaultProducerSettings, connection);

        assertThat(settings.properties().get(SaslConfigs.SASL_MECHANISM).get()).isEqualTo(mechanism);
        assertThat(settings.properties().get(SaslConfigs.SASL_JAAS_CONFIG).get()).isEqualTo(
                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"" + KNOWN_USER +
                        "\" password=\"" + KNOWN_PASSWORD + "\";"
        );
    }

    private static Connection getConnectionWithSaslMechanism(@Nullable final String saslMechanism) {
        return getConnection(KNOWN_USER, KNOWN_PASSWORD, specificConfigWithSaslMechanism(saslMechanism));
    }

    private static Connection getConnectionWithoutUsernameAndPassword() {
        return getConnection(null, null, defaultSpecificConfig);
    }

    private static Connection getConnection(@Nullable final String username, @Nullable final String password,
            final Map<String, String> specificConfig) {

        final String uri = getUriWithUserAndPassword(username, password);
        return ConnectivityModelFactory.newConnectionBuilder(ConnectionId.of("kafka"), ConnectionType.KAFKA,
                ConnectivityStatus.OPEN, uri)
                .targets(singletonList(
                        org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newTarget("target",
                                AUTHORIZATION_CONTEXT, null, 1, Topic.LIVE_EVENTS)))
                .specificConfig(specificConfig)
                .build();
    }

    private static String getUriWithUserAndPassword(@Nullable final String username, @Nullable final String password) {
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
            return defaultSpecificConfig;
        }

        final Map<String, String> configWithSasl = new HashMap<>(defaultSpecificConfig);
        configWithSasl.put("saslMechanism", saslMechanism);
        return configWithSasl;
    }

}
