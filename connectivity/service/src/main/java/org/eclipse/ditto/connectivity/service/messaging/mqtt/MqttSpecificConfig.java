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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.common.InvalidMqttQosCodeException;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

/**
 * Class providing access to MQTT specific configuration.
 */
@Immutable
public final class MqttSpecificConfig {

    static final String LAST_WILL_TOPIC = "lastWillTopic";
    static final String LAST_WILL_QOS = "lastWillQos";
    static final String LAST_WILL_RETAIN = "lastWillRetain";
    static final String LAST_WILL_MESSAGE = "lastWillMessage";

    private static final String CLEAN_SESSION = "cleanSession";
    private static final String RECONNECT_FOR_REDELIVERY = "reconnectForRedelivery";
    private static final String RECONNECT_FOR_REDELIVERY_DELAY = "reconnectForRedeliveryDelay";
    private static final String SEPARATE_PUBLISHER_CLIENT = "separatePublisherClient";
    private static final String CLIENT_ID = "clientId";
    private static final String PUBLISHER_ID = "publisherId";
    private static final String KEEP_ALIVE_INTERVAL = "keepAlive";

    private static final boolean DEFAULT_LAST_WILL_RETAIN = false;
    static final MqttQos DEFAULT_LAST_WILL_QOS = MqttQos.AT_MOST_ONCE;

    private final Config specificConfig;

    private MqttSpecificConfig(final Config specificConfig) {
        this.specificConfig = specificConfig;
    }

    /**
     * Creates a new instance of MqttSpecificConfig based on the {@code specificConfig} of the passed
     * {@code connection}.
     *
     * @param connection the Connection to extract the {@code specificConfig} map from.
     * @param mqttConfig the mqtt config to create the default config from.
     * @return the new MqttSpecificConfig instance
     */
    public static MqttSpecificConfig fromConnection(final Connection connection, final MqttConfig mqttConfig) {
        final Map<String, Object> defaultConfig = toDefaultConfig(mqttConfig);
        final Config config = ConfigFactory.parseMap(connection.getSpecificConfig())
                .withFallback(ConfigFactory.parseMap(defaultConfig));

        return new MqttSpecificConfig(config);
    }

    private static Map<String, Object> toDefaultConfig(final MqttConfig mqttConfig) {
        final Map<String, Object> defaultMap = new HashMap<>();
        defaultMap.put(CLEAN_SESSION, mqttConfig.isCleanSession());
        defaultMap.put(RECONNECT_FOR_REDELIVERY, mqttConfig.shouldReconnectForRedelivery());
        defaultMap.put(RECONNECT_FOR_REDELIVERY_DELAY, mqttConfig.getReconnectForRedeliveryDelay());
        defaultMap.put(SEPARATE_PUBLISHER_CLIENT, mqttConfig.shouldUseSeparatePublisherClient());
        return defaultMap;
    }

    /**
     * @return whether subscriber CONN messages should set clean-session or clean-start flag to true.
     */
    public boolean cleanSession() {
        return specificConfig.getBoolean(CLEAN_SESSION);
    }

    /**
     * @return whether reconnect-for-redelivery behavior is activated.
     */
    public boolean reconnectForRedelivery() {
        return specificConfig.getBoolean(RECONNECT_FOR_REDELIVERY);
    }

    /**
     * @return whether to use a separate client for publisher actors so that reconnect-for-redelivery
     * does not disrupt the publisher.
     */
    public boolean isSeparatePublisherClient() {
        return specificConfig.getBoolean(SEPARATE_PUBLISHER_CLIENT);
    }

    /**
     * Returns the delay how long to wait before reconnecting a consumer client for redelivery.
     *
     * @return the reconnect delay which is at least {@link ReconnectDelay#LOWER_BOUNDARY}.
     */
    public ReconnectDelay getReconnectForDeliveryDelay() {
        return ReconnectDelay.ofOrLowerBoundary(specificConfig.getDuration(RECONNECT_FOR_REDELIVERY_DELAY));
    }

    /**
     * @return the optional clientId which should be used by the MQTT client when connecting to the MQTT broker.
     */
    public Optional<String> getMqttClientId() {
        return getStringOptional(CLIENT_ID);
    }

    /**
     * @return the optional publisherId which should be used as the client ID of the publisher actor.
     */
    public Optional<String> getMqttPublisherId() {
        return getStringOptional(PUBLISHER_ID);
    }


    /**
     * Returns the optional MQTT topic where the Last Will message should be sent to.
     *
     * @return the optional MQTT topic of the Last Will message.
     * @throws IllegalArgumentException if the configuration value at {@value #LAST_WILL_TOPIC} is not a valid
     * MQTT topic.
     */
    public Optional<MqttTopic> getMqttLastWillTopic() {
        return getStringOptional(LAST_WILL_TOPIC).map(MqttTopic::of);
    }

    /**
     * Returns the QoS of the MQTT Last Will message.
     *
     * @return the configured QoS of the MQTT Last Will message or {@link #DEFAULT_LAST_WILL_QOS} if no Last Will QoS is
     * configured at all.
     * @throws InvalidMqttQosCodeException if the configured QoS is not a valid {@link MqttQos}.
     */
    public MqttQos getLastWillQosOrThrow() {
        final int mqttQosCode = getSafely(() -> specificConfig.getInt(LAST_WILL_QOS), DEFAULT_LAST_WILL_QOS.getCode());
        final var mqttQos = MqttQos.fromCode(mqttQosCode);
        if (null != mqttQos) {
            return mqttQos;
        } else {
            throw new InvalidMqttQosCodeException(mqttQosCode);
        }
    }

    /**
     * @return the optional message which should be used on Last Will message.
     */
    public Optional<String> getMqttWillMessage() {
        return getStringOptional(LAST_WILL_MESSAGE);
    }

    /**
     * @return the retain flag which should be used on Last Will message.
     */
    public boolean getMqttWillRetain() {
        return getSafely(() -> specificConfig.getBoolean(LAST_WILL_RETAIN), DEFAULT_LAST_WILL_RETAIN);
    }

    /**
     * Returns the keep-alive interval, i.e. the number of seconds that the broker permits between when a client
     * finishes sending one MQTT packet and starts to send the next.
     *
     * @return a keep-alive interval with the configured seconds or {@link KeepAliveInterval#defaultKeepAlive()} if
     * configuration key {@value #KEEP_ALIVE_INTERVAL} has no value at all.
     * @throws IllegalKeepAliveIntervalSecondsException if the configured value for {@value #KEEP_ALIVE_INTERVAL} either
     * <ul>
     *     <li>is not a duration or</li>
     *     <li>it exceeds the allowed range or a Keep Alive Interval.</li>
     * </ul>
     * @see KeepAliveInterval#defaultKeepAlive()
     */
    public KeepAliveInterval getKeepAliveIntervalOrDefault() throws IllegalKeepAliveIntervalSecondsException {
        KeepAliveInterval result;
        try {
            result = KeepAliveInterval.of(specificConfig.getDuration(KEEP_ALIVE_INTERVAL));
        } catch (final ConfigException.Missing e) {
            result = KeepAliveInterval.defaultKeepAlive();
        } catch (final ConfigException.WrongType e) {
            throw new IllegalKeepAliveIntervalSecondsException(
                    MessageFormat.format("Configured value for Keep Alive Interval is invalid: {0}", e.getMessage()),
                    e
            );
        }
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (MqttSpecificConfig) o;
        return Objects.equals(specificConfig, that.specificConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(specificConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "specificConfig=" + specificConfig +
                "]";
    }

    private Optional<String> getStringOptional(final String key) {
        if (specificConfig.hasPath(key)) {
            return Optional.of(specificConfig.getString(key));
        } else {
            return Optional.empty();
        }
    }

    private static <T> T getSafely(final Supplier<T> supplier, final T defaultValue) {
        try {
            return supplier.get();
        } catch (final ConfigException e) {
            return defaultValue;
        }
    }

}
