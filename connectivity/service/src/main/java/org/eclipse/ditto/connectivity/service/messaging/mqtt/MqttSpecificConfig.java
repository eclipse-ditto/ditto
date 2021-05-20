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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

/**
 * Class providing access to MQTT specific configuration.
 */
@Immutable
public final class MqttSpecificConfig {

    private static final String CLEAN_SESSION = "cleanSession";
    private static final String RECONNECT_FOR_REDELIVERY = "reconnectForRedelivery";
    private static final String RECONNECT_FOR_REDELIVERY_DELAY = "reconnectForRedeliveryDelay";
    private static final String SEPARATE_PUBLISHER_CLIENT = "separatePublisherClient";
    private static final String CLIENT_ID = "clientId";
    private static final String PUBLISHER_ID = "publisherId";
    private static final String KEEP_ALIVE_INTERVAL = "keepAlive";

    static final String LAST_WILL_TOPIC = "lastWillTopic";
    static final String LAST_WILL_QOS = "lastWillQos";
    static final String LAST_WILL_RETAIN = "lastWillRetain";
    static final String LAST_WILL_MESSAGE = "lastWillMessage";

    private static final boolean DEFAULT_LAST_WILL_RETAIN = false;
    private static final int DEFAULT_LAST_WILL_QOS = 0;

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
    public boolean separatePublisherClient() {
        return specificConfig.getBoolean(SEPARATE_PUBLISHER_CLIENT);
    }

    /**
     * @return how long to wait before reconnect a consumer client for redelivery.
     */
    public Duration getReconnectForDeliveryDelay() {
        return specificConfig.getDuration(RECONNECT_FOR_REDELIVERY_DELAY);
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
     * @return the optional topic which should be used on Last Will message.
     */
    public Optional<String> getMqttWillTopic() {
        return getStringOptional(LAST_WILL_TOPIC);
    }

    /**
     * @return the Qos which should be used on Last Will message.
     */
    public int getMqttWillQos() {
        return getSafely(() -> specificConfig.getInt(LAST_WILL_QOS), DEFAULT_LAST_WILL_QOS);
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
     * @return the interval between keep alive pings.
     */
    public Optional<Duration> getKeepAliveInterval() {
        return getDurationOptional(KEEP_ALIVE_INTERVAL);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MqttSpecificConfig that = (MqttSpecificConfig) o;
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

    private Optional<Duration> getDurationOptional(final String key) {
        if (specificConfig.hasPath(key)) {
            return Optional.of(specificConfig.getDuration(key));
        } else {
            return Optional.empty();
        }
    }

    private static <T> T getSafely(Supplier<T> supplier, final T defaultValue) {
        try {
            return supplier.get();
        } catch (final ConfigException e) {
            return defaultValue;
        }
    }

}
