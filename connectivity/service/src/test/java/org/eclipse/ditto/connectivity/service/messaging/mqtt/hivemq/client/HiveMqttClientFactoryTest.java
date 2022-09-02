/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import javax.net.ssl.KeyManagerFactory;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.Credentials;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.DittoTrustManagerFactory;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.MqttVersion;
import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttUtf8String;

/**
 * Unit test for {@link HiveMqttClientFactory}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class HiveMqttClientFactoryTest {

    private static final URI CONNECTION_URI = URI.create("tcp://localhost:1883");
    private static final String USERNAME = "aluqah";
    private static final String PASSWORD = "wine";
    private static final int EVENT_LOOP_THREAD_NUMBER = 5;
    private static final MqttClientIdentifier MQTT_CLIENT_IDENTIFIER = MqttClientIdentifier.of("myClient");
    private static final UUID ACTOR_UUID = UUID.randomUUID();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock private Connection mqttConnection;
    @Mock private MqttConfig mqttConfig;
    @Mock private ConnectivityConfig connectivityConfig;
    @Mock private Supplier<SshTunnelState> sshTunnelStateSupplier;
    @Mock private ConnectionLogger connectionLogger;
    @Mock private GenericMqttClientConnectedListener mqttClientConnectedListener;
    @Mock private GenericMqttClientDisconnectedListener mqttClientDisconnectedListener;

    @Before
    public void before() {
        Mockito.when(mqttConnection.getConnectionType()).thenReturn(ConnectionType.MQTT_5);
        Mockito.when(mqttConnection.getUri()).thenReturn(CONNECTION_URI.toString());
        Mockito.when(mqttConnection.getProtocol()).thenReturn(CONNECTION_URI.getScheme());
        Mockito.when(mqttConnection.getUsername()).thenReturn(Optional.of(USERNAME));
        Mockito.when(mqttConnection.getPassword()).thenReturn(Optional.of(PASSWORD));

        Mockito.when(mqttConfig.getEventLoopThreads()).thenReturn(EVENT_LOOP_THREAD_NUMBER);

        final var connectionConfig = Mockito.mock(ConnectionConfig.class);
        Mockito.when(connectionConfig.getMqttConfig()).thenReturn(mqttConfig);
        Mockito.when(connectivityConfig.getConnectionConfig()).thenReturn(connectionConfig);

        final var sshTunnelState = SshTunnelState.disabled();
        Mockito.when(sshTunnelStateSupplier.get()).thenReturn(sshTunnelState);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(HiveMqttClientFactory.class, areImmutable());
    }

    @Test
    public void getMqtt3ClientWithNullHiveMqttClientPropertiesThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> HiveMqttClientFactory.getMqtt3Client(null,
                        MQTT_CLIENT_IDENTIFIER,
                        ClientRole.CONSUMER_PUBLISHER))
                .withMessage("The hiveMqttClientProperties must not be null!")
                .withNoCause();
    }

    @Test
    public void getMqtt3ClientWithNullMqttClientIdentifierThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> HiveMqttClientFactory.getMqtt3Client(Mockito.mock(HiveMqttClientProperties.class),
                        null,
                        ClientRole.CONSUMER_PUBLISHER))
                .withMessage("The mqttClientIdentifier must not be null!")
                .withNoCause();
    }

    @Test
    public void getMqtt3ClientWithNullClientRoleThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> HiveMqttClientFactory.getMqtt3Client(Mockito.mock(HiveMqttClientProperties.class),
                        MQTT_CLIENT_IDENTIFIER,
                        null))
                .withMessage("The clientRole must not be null!")
                .withNoCause();
    }

    @Test
    public void getMqtt3ClientWithoutLastWillWithoutSslReturnsExpected() throws NoMqttConnectionException {
        final var hiveMqttClientProperties = HiveMqttClientProperties.builder()
                .withMqttConnection(mqttConnection)
                .withConnectivityConfig(connectivityConfig)
                .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                .withConnectionLogger(connectionLogger)
                .withActorUuid(ACTOR_UUID)
                .withClientConnectedListener(mqttClientConnectedListener)
                .withClientDisconnectedListener(mqttClientDisconnectedListener)
                .build();

        final var underTest = HiveMqttClientFactory.getMqtt3Client(hiveMqttClientProperties,
                MQTT_CLIENT_IDENTIFIER,
                ClientRole.CONSUMER_PUBLISHER);

        final var mqtt3ClientConfig = underTest.getConfig();

        softly.assertThat(mqtt3ClientConfig.getClientIdentifier())
                .as("client identifier")
                .hasValue(MQTT_CLIENT_IDENTIFIER);
        softly.assertThat(mqtt3ClientConfig.getConnectionConfig()).as("connection config").isEmpty();
        softly.assertThat(mqtt3ClientConfig.getWillPublish()).as("will publish").isEmpty();
        softly.assertThat(mqtt3ClientConfig.getMqttVersion()).as("MQTT version").isEqualTo(MqttVersion.MQTT_3_1_1);
        softly.assertThat(mqtt3ClientConfig.getServerAddress())
                .as("server address")
                .isEqualTo(new InetSocketAddress(CONNECTION_URI.getHost(), CONNECTION_URI.getPort()));
        softly.assertThat(mqtt3ClientConfig.getAutomaticReconnect()).as("automatic reconnect").isEmpty();
        softly.assertThat(mqtt3ClientConfig.getState()).as("state").isEqualTo(MqttClientState.DISCONNECTED);
        softly.assertThat(mqtt3ClientConfig.getSslConfig()).as("SSL config").isEmpty();
        softly.assertThat(mqtt3ClientConfig.getExecutorConfig())
                .as("executor config")
                .satisfies(executorConfig -> softly.assertThat(executorConfig.getNettyThreads())
                        .as("netty threads")
                        .hasValue(EVENT_LOOP_THREAD_NUMBER));
        softly.assertThat(mqtt3ClientConfig.getWebSocketConfig()).as("WebSocket config").isEmpty();
        softly.assertThat(mqtt3ClientConfig.getSimpleAuth()).as("simple auth").hasValueSatisfying(mqtt3SimpleAuth -> {
            softly.assertThat(mqtt3SimpleAuth.getUsername()).as("username").isEqualTo(MqttUtf8String.of(USERNAME));
            softly.assertThat(mqtt3SimpleAuth.getPassword())
                    .as("password")
                    .hasValue(ByteBuffer.wrap(PASSWORD.getBytes(StandardCharsets.UTF_8)));
        });
        softly.assertThat(mqtt3ClientConfig.getConnectedListeners())
                .as("connected listeners")
                .hasSize(1);
        softly.assertThat(mqtt3ClientConfig.getDisconnectedListeners())
                .as("disconnected listeners")
                .hasSize(1);
    }

    @Test
    public void getMqtt3ClientWithLastWillWithoutSslReturnsExpected() throws NoMqttConnectionException {
        final var lastWillTopic = MqttTopic.of("source/last-will");
        final var lastWillQos = MqttQos.EXACTLY_ONCE;
        final var lastWillMessage = "Adipisci temporibus vitae aut ipsa repudiandae.";
        final var lastWillRetain = true;
        Mockito.when(mqttConnection.getSpecificConfig())
                .thenReturn(Map.of(
                        "lastWillTopic", lastWillTopic.toString(),
                        "lastWillQos", String.valueOf(lastWillQos.getCode()),
                        "lastWillMessage", lastWillMessage,
                        "lastWillRetain", String.valueOf(lastWillRetain)
                ));

        final var hiveMqttClientProperties = HiveMqttClientProperties.builder()
                .withMqttConnection(mqttConnection)
                .withConnectivityConfig(connectivityConfig)
                .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                .withConnectionLogger(connectionLogger)
                .withActorUuid(ACTOR_UUID)
                .withClientConnectedListener(mqttClientConnectedListener)
                .withClientDisconnectedListener(mqttClientDisconnectedListener)
                .build();

        final var underTest = HiveMqttClientFactory.getMqtt3Client(hiveMqttClientProperties,
                MQTT_CLIENT_IDENTIFIER,
                ClientRole.CONSUMER_PUBLISHER);

        final var mqtt3ClientConfig = underTest.getConfig();

        softly.assertThat(mqtt3ClientConfig.getWillPublish())
                .as("will publish")
                .hasValueSatisfying(mqtt3WillPublish -> {
                    softly.assertThat(mqtt3WillPublish.getTopic()).as("will topic").isEqualTo(lastWillTopic);
                    softly.assertThat(mqtt3WillPublish.getQos()).as("will QoS").isEqualTo(lastWillQos);
                    softly.assertThat(mqtt3WillPublish.getPayloadAsBytes())
                            .as("payload bytes")
                            .isEqualTo(lastWillMessage.getBytes(StandardCharsets.UTF_8));
                    softly.assertThat(mqtt3WillPublish.isRetain()).as("retain").isEqualTo(lastWillRetain);
                });
    }

    @Test
    public void getMqtt3ClientWithoutLastWillWithSslReturnsExpected() throws NoMqttConnectionException {
        Mockito.when(mqttConnection.getProtocol()).thenReturn("ssl");
        final var keyManagerFactory = Mockito.mock(KeyManagerFactory.class);
        final var credentials = Mockito.mock(Credentials.class);
        Mockito.when(credentials.accept(Mockito.any())).thenReturn(keyManagerFactory);
        Mockito.when(mqttConnection.getCredentials()).thenReturn(Optional.of(credentials));

        final var hiveMqttClientProperties = HiveMqttClientProperties.builder()
                .withMqttConnection(mqttConnection)
                .withConnectivityConfig(connectivityConfig)
                .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                .withConnectionLogger(connectionLogger)
                .withActorUuid(ACTOR_UUID)
                .withClientConnectedListener(mqttClientConnectedListener)
                .withClientDisconnectedListener(mqttClientDisconnectedListener)
                .build();

        final var underTest = HiveMqttClientFactory.getMqtt3Client(hiveMqttClientProperties,
                MQTT_CLIENT_IDENTIFIER,
                ClientRole.CONSUMER_PUBLISHER);

        final var mqtt3ClientConfig = underTest.getConfig();

        softly.assertThat(mqtt3ClientConfig.getSslConfig())
                .as("SSL config")
                .hasValueSatisfying(mqttClientSslConfig -> {
                    softly.assertThat(mqttClientSslConfig.getTrustManagerFactory())
                            .as("trust manager factory")
                            .hasValueSatisfying(trustManagerFactory -> softly.assertThat(trustManagerFactory)
                                    .isInstanceOf(DittoTrustManagerFactory.class));
                    softly.assertThat(mqttClientSslConfig.getKeyManagerFactory())
                            .as("key manager factory")
                            .hasValue(keyManagerFactory);
                });
    }

    @Test
    public void getMqtt5ClientWithNullClientRoleThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> HiveMqttClientFactory.getMqtt5Client(Mockito.mock(HiveMqttClientProperties.class),
                        MQTT_CLIENT_IDENTIFIER,
                        null))
                .withMessage("The clientRole must not be null!")
                .withNoCause();
    }

    @Test
    public void getMqtt5ClientWithoutLastWillWithoutSslReturnsExpected() throws NoMqttConnectionException {
        final var hiveMqttClientProperties = HiveMqttClientProperties.builder()
                .withMqttConnection(mqttConnection)
                .withConnectivityConfig(connectivityConfig)
                .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                .withConnectionLogger(connectionLogger)
                .withActorUuid(ACTOR_UUID)
                .withClientConnectedListener(mqttClientConnectedListener)
                .withClientDisconnectedListener(mqttClientDisconnectedListener)
                .build();

        final var underTest = HiveMqttClientFactory.getMqtt5Client(hiveMqttClientProperties,
                MQTT_CLIENT_IDENTIFIER,
                ClientRole.CONSUMER_PUBLISHER);

        final var mqtt5ClientConfig = underTest.getConfig();

        softly.assertThat(mqtt5ClientConfig.getClientIdentifier())
                .as("client identifier")
                .hasValue(MQTT_CLIENT_IDENTIFIER);
        softly.assertThat(mqtt5ClientConfig.getConnectionConfig()).as("connection config").isEmpty();
        softly.assertThat(mqtt5ClientConfig.getWillPublish()).as("will publish").isEmpty();
        softly.assertThat(mqtt5ClientConfig.getMqttVersion()).as("MQTT version").isEqualTo(MqttVersion.MQTT_5_0);
        softly.assertThat(mqtt5ClientConfig.getServerAddress())
                .as("server address")
                .isEqualTo(new InetSocketAddress(CONNECTION_URI.getHost(), CONNECTION_URI.getPort()));
        softly.assertThat(mqtt5ClientConfig.getAutomaticReconnect()).as("automatic reconnect").isEmpty();
        softly.assertThat(mqtt5ClientConfig.getState()).as("state").isEqualTo(MqttClientState.DISCONNECTED);
        softly.assertThat(mqtt5ClientConfig.getSslConfig()).as("SSL config").isEmpty();
        softly.assertThat(mqtt5ClientConfig.getExecutorConfig())
                .as("executor config")
                .satisfies(executorConfig -> softly.assertThat(executorConfig.getNettyThreads())
                        .as("netty threads")
                        .hasValue(EVENT_LOOP_THREAD_NUMBER));
        softly.assertThat(mqtt5ClientConfig.getWebSocketConfig()).as("WebSocket config").isEmpty();
        softly.assertThat(mqtt5ClientConfig.getSimpleAuth()).as("simple auth").hasValueSatisfying(mqtt5SimpleAuth -> {
            softly.assertThat(mqtt5SimpleAuth.getUsername()).as("username").hasValue(MqttUtf8String.of(USERNAME));
            softly.assertThat(mqtt5SimpleAuth.getPassword())
                    .as("password")
                    .hasValue(ByteBuffer.wrap(PASSWORD.getBytes(StandardCharsets.UTF_8)));
        });
        softly.assertThat(mqtt5ClientConfig.getConnectedListeners())
                .as("connected listeners")
                .hasSize(1);
        softly.assertThat(mqtt5ClientConfig.getDisconnectedListeners())
                .as("disconnected listeners")
                .hasSize(1);
    }

    @Test
    public void getMqtt5ClientWithLastWillWithoutSslReturnsExpected() throws NoMqttConnectionException {
        final var lastWillTopic = MqttTopic.of("source/last-will");
        final var lastWillQos = MqttQos.EXACTLY_ONCE;
        final var lastWillMessage = "Adipisci temporibus vitae aut ipsa repudiandae.";
        final var lastWillRetain = true;
        Mockito.when(mqttConnection.getSpecificConfig())
                .thenReturn(Map.of(
                        "lastWillTopic", lastWillTopic.toString(),
                        "lastWillQos", String.valueOf(lastWillQos.getCode()),
                        "lastWillMessage", lastWillMessage,
                        "lastWillRetain", String.valueOf(lastWillRetain)
                ));

        final var hiveMqttClientProperties = HiveMqttClientProperties.builder()
                .withMqttConnection(mqttConnection)
                .withConnectivityConfig(connectivityConfig)
                .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                .withConnectionLogger(connectionLogger)
                .withActorUuid(ACTOR_UUID)
                .withClientConnectedListener(mqttClientConnectedListener)
                .withClientDisconnectedListener(mqttClientDisconnectedListener)
                .build();

        final var underTest = HiveMqttClientFactory.getMqtt5Client(hiveMqttClientProperties,
                MQTT_CLIENT_IDENTIFIER,
                ClientRole.CONSUMER_PUBLISHER);

        final var mqtt5ClientConfig = underTest.getConfig();

        softly.assertThat(mqtt5ClientConfig.getWillPublish())
                .as("will publish")
                .hasValueSatisfying(mqtt5WillPublish -> {
                    softly.assertThat(mqtt5WillPublish.getTopic()).as("will topic").isEqualTo(lastWillTopic);
                    softly.assertThat(mqtt5WillPublish.getQos()).as("will QoS").isEqualTo(lastWillQos);
                    softly.assertThat(mqtt5WillPublish.getPayloadAsBytes())
                            .as("payload bytes")
                            .isEqualTo(lastWillMessage.getBytes(StandardCharsets.UTF_8));
                    softly.assertThat(mqtt5WillPublish.isRetain()).as("retain").isEqualTo(lastWillRetain);
                });
    }

    @Test
    public void getMqtt5ClientWithoutLastWillWithSslReturnsExpected() throws NoMqttConnectionException {
        Mockito.when(mqttConnection.getProtocol()).thenReturn("ssl");
        final var keyManagerFactory = Mockito.mock(KeyManagerFactory.class);
        final var credentials = Mockito.mock(Credentials.class);
        Mockito.when(credentials.accept(Mockito.any())).thenReturn(keyManagerFactory);
        Mockito.when(mqttConnection.getCredentials()).thenReturn(Optional.of(credentials));

        final var hiveMqttClientProperties = HiveMqttClientProperties.builder()
                .withMqttConnection(mqttConnection)
                .withConnectivityConfig(connectivityConfig)
                .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                .withConnectionLogger(connectionLogger)
                .withActorUuid(ACTOR_UUID)
                .withClientConnectedListener(mqttClientConnectedListener)
                .withClientDisconnectedListener(mqttClientDisconnectedListener)
                .build();

        final var underTest = HiveMqttClientFactory.getMqtt5Client(hiveMqttClientProperties,
                MQTT_CLIENT_IDENTIFIER,
                ClientRole.CONSUMER_PUBLISHER);

        final var mqtt5ClientConfig = underTest.getConfig();

        softly.assertThat(mqtt5ClientConfig.getSslConfig())
                .as("SSL config")
                .hasValueSatisfying(mqttClientSslConfig -> {
                    softly.assertThat(mqttClientSslConfig.getTrustManagerFactory())
                            .as("trust manager factory")
                            .hasValueSatisfying(trustManagerFactory -> softly.assertThat(trustManagerFactory)
                                    .isInstanceOf(DittoTrustManagerFactory.class));
                    softly.assertThat(mqttClientSslConfig.getKeyManagerFactory())
                            .as("key manager factory")
                            .hasValue(keyManagerFactory);
                });
    }

}