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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.model.mqtt.ReceiveMaximum;
import org.eclipse.ditto.connectivity.model.mqtt.SessionExpiryInterval;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.KeepAliveInterval;

import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;

/**
 * Generic representation of a message (MQTT Connect command) for clients to request a connection to an MQTT broker.
 */
@Immutable
public final class GenericMqttConnect {

    private final boolean cleanSession;
    private final KeepAliveInterval keepAliveInterval;
    private final SessionExpiryInterval sessionExpiryInterval;
    private final ReceiveMaximum receiveMaximum;

    private GenericMqttConnect(final boolean cleanSession,
            final KeepAliveInterval keepAliveInterval,
            final SessionExpiryInterval sessionExpiryInterval,
            final ReceiveMaximum receiveMaximum) {

        this.cleanSession = cleanSession;
        this.keepAliveInterval = keepAliveInterval;
        this.sessionExpiryInterval = sessionExpiryInterval;
        this.receiveMaximum = receiveMaximum;
    }

    /**
     * Returns a new instance of {@code GenericMqttConnect} for the specified arguments.
     *
     * @param cleanSession tells the broker whether the client wants to establish a persistent session or not.
     * In a persistent session ({@code cleanSession} = {@code false}), the broker stores all subscriptions for the
     * client and all missed messages for the client that subscribed with a Quality of Service (QoS) level 1 or 2.
     * If the session is not persistent ({@code cleanSession} = {@code true}), the broker does not store anything for
     * the client and purges all information from any previous persistent session.
     * @param keepAliveInterval time interval that the client specifies and communicates to the broker when the
     * connection established.
     * This interval defines the longest period of time that the broker and client can endure without sending a message.
     * @param sessionExpiryInterval time interval that the broker buffers un-acked and retained messages after
     * connection closed.
     * @param receiveMaximum the maximum number of unacknowledged QoS 1 and QoS 2 PUBLISH messages the client is able
     * to receive.
     * The Receive Maximum is only applied for MQTT protocol version 5
     * @return the new instance.
     * @throws NullPointerException if {@code keepAliveInterval} or {@code receiveMaximum} is {@code null}.
     */
    public static GenericMqttConnect newInstance(final boolean cleanSession,
            final KeepAliveInterval keepAliveInterval,
            final SessionExpiryInterval sessionExpiryInterval,
            final ReceiveMaximum receiveMaximum) {

        return new GenericMqttConnect(cleanSession,
                ConditionChecker.checkNotNull(keepAliveInterval, "keepAliveInterval"),
                ConditionChecker.checkNotNull(sessionExpiryInterval, "sessionExpiryInterval"),
                ConditionChecker.checkNotNull(receiveMaximum, "receiveMaximum"));
    }

    /**
     * Returns this generic MQTT Connect message as {@link Mqtt3Connect}.
     *
     * @return the derived {@code Mqtt3Connect}.
     */
    public Mqtt3Connect getAsMqtt3Connect() {
        return Mqtt3Connect.builder()
                .cleanSession(cleanSession)
                .keepAlive(keepAliveInterval.getSeconds())
                .build();
    }

    /**
     * Returns this generic MQTT Connect message as {@link Mqtt5Connect}.
     *
     * @return the derived {@code Mqtt5Connect}.
     */
    public Mqtt5Connect getAsMqtt5Connect() {
        return Mqtt5Connect.builder()
                .cleanStart(cleanSession)
                .keepAlive(keepAliveInterval.getSeconds())
                .sessionExpiryInterval(sessionExpiryInterval.getSeconds())
                .restrictions().receiveMaximum(receiveMaximum.getValue()).applyRestrictions()
                .build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (GenericMqttConnect) o;
        return cleanSession == that.cleanSession &&
                Objects.equals(keepAliveInterval, that.keepAliveInterval) &&
                Objects.equals(sessionExpiryInterval, that.sessionExpiryInterval) &&
                Objects.equals(receiveMaximum, that.receiveMaximum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cleanSession, keepAliveInterval, sessionExpiryInterval, receiveMaximum);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "cleanSession=" + cleanSession +
                ", keepAliveInterval=" + keepAliveInterval +
                ", sessionExpiryInterval=" + sessionExpiryInterval +
                ", receiveMaximum=" + receiveMaximum +
                "]";
    }

}
