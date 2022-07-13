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

import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext;

/**
 * Listener which is notified when a client is disconnected from a broker.
 */
@FunctionalInterface
public interface GenericMqttClientDisconnectedListener {

    /**
     * Listener method that is notified in the following cases:
     * <ul>
     *     <li>
     *         A client was disconnected (with or without a Disconnect message, by the server, client or user) or the
     *         connection failed.
     *         The client state will still be {@link com.hivemq.client.mqtt.MqttClientState#CONNECTED} and the
     *         {@link com.hivemq.client.mqtt.MqttClientConnectionConfig} will still be present.
     *     </li>
     *     <li>
     *         A connect attempt by the user failed.
     *         The client state will still be {@link com.hivemq.client.mqtt.MqttClientState#CONNECTING}.
     *     </li>
     *     <li>
     *         A reconnect attempt by the client failed.
     *         The client state will still be {@link com.hivemq.client.mqtt.MqttClientState#CONNECTING_RECONNECT}.
     *     </li>
     * </ul>
     *
     * The client state will be updated after all {@link GenericMqttClientDisconnectedListener}s are called to
     *
     * <ul>
     *     <li>{@link com.hivemq.client.mqtt.MqttClientState#DISCONNECTED} or</li>
     *     <li>
     *         {@link com.hivemq.client.mqtt.MqttClientState#DISCONNECTED_RECONNECT} if the client is instructed to
     *         reconnect.
     *     </li>
     * </ul>
     *
     * <em>This method must not block.</em>
     * If you want to reconnect you have to use the supplied {@link MqttClientDisconnectedContext#getReconnector()}.
     *
     * @param context context about the client that is now disconnected, the cause for disconnection and allow
     * reconnecting.
     * @param clientRole role of the client that is now disconnected.
     */
    void onDisconnected(MqttClientDisconnectedContext context, ClientRole clientRole);

}
