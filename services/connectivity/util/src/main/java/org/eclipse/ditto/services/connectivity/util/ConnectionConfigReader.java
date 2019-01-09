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
package org.eclipse.ditto.services.connectivity.util;

import java.time.Duration;

import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.ConfigurationException;

/**
 * Reader of config options for {@code ConnectionActor}.
 */
public final class ConnectionConfigReader extends AbstractConfigReader {

    private static final String PREFIX = ConfigKeys.CONNECTIVITY_PREFIX + "connection";
    private static final String SNAPSHOT_THRESHOLD = "snapshot.threshold";

    private ConnectionConfigReader(final Config config) {
        super(config);
        validate();
    }

    /**
     * Create a connection config reader from the config of connectivity service.
     *
     * @param rawConfig config of the connectivity service.
     * @return a connection config reader.
     */
    public static ConnectionConfigReader fromRawConfig(final Config rawConfig) {
        final Config connectionConfig =
                getIfPresentFrom(rawConfig, PREFIX, rawConfig::getConfig).orElse(ConfigFactory.empty());
        return new ConnectionConfigReader(connectionConfig);
    }

    /**
     * Every amount of changes (configured by this key), this Actor will create a snapshot of the connectionStatus.
     *
     * @return the snapshot threshold.
     */
    public int snapshotThreshold() {
        return config.getInt(SNAPSHOT_THRESHOLD);
    }

    /**
     * The delay between subscribing to Akka pub/sub and responding to the command that triggered the subscription.
     * The delay gives Akka pub/sub a chance to reach consensus in the cluster before clients start expecting
     * messages and events. The default value is 5s.
     *
     * @return the delay.
     */
    public Duration flushPendingResponsesTimeout() {
        return getIfPresent("flush-pending-responses-timeout", config::getDuration)
                .orElseGet(() -> Duration.ofSeconds(5L));
    }

    public Duration clientActorAskTimeout() {
        return getIfPresent("client-actor-ask-timeout", config::getDuration)
                .orElseGet(() -> Duration.ofSeconds(60L));
    }

    /**
     * Config specific to the protocol MQTT.
     *
     * @return the MQTT config reader.
     */
    public MqttConfigReader mqtt() {
        return new MqttConfigReader(getChildOrEmpty("mqtt"));
    }

    private void validate() {
        final int snapshotThreshold = snapshotThreshold();
        if (snapshotThreshold <= 0) {
            final String snapshotThresholdKey = String.format("%s.%s", PREFIX, SNAPSHOT_THRESHOLD);
            throw new ConfigurationException(String.format("Config setting '%s' must be positive, but is: %d.",
                    snapshotThresholdKey, snapshotThreshold));
        }
    }
}
