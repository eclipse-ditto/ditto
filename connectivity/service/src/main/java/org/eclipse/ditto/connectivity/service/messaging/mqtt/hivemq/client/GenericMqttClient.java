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

import java.util.concurrent.CompletionStage;

/**
 * An MQTT client with a tailored interface.
 * It abstracts protocol version 3 and 5.
 */
public interface GenericMqttClient
        extends GenericMqttConnectableClient, GenericMqttSubscribingClient, GenericMqttPublishingClient {

    /**
     * Connects this client with the specified Connect message.
     *
     * @return a {@code CompletionStage} which
     * <ul>
     *     <li>completes normally if the ConnAck message does not contain an Error Code (connected successfully),</li>
     *     <li>
     *         completes exceptionally with a {@link MqttClientConnectException} if the ConnAck message contains an
     *         Error Code or
     *     </li>
     *     <li>
     *         completes exceptionally with a different exception if an error occurred before the Connect message was
     *         sent or before the ConnAck message was received.
     *     </li>
     * </ul>
     */
    CompletionStage<Void> connect();

    /**
     * Disconnects the specified role of this client.
     * Based on configuration this may trigger an automatic reconnect of that role.
     * <p>
     * It might happen, that more roles than the specified one will be disconnected, for example, if configuration
     * states that no separate publisher client should be used because in this case consuming and publishing is
     * performed by the <em>same</em> HiveMQ MQTT client.
     *
     * @param clientRole the role of the client to be disconnected.
     * @return a CompletionStage which
     * <ul>
     *     <li>completes when the client role was successfully disconnected or</li>
     *     <li>completes exceptionally if the client role did not disconnect gracefully</li>
     * </ul>
     * @throws NullPointerException if {@code clientRole} is {@code null}.
     */
    CompletionStage<Void> disconnectClientRole(ClientRole clientRole);

}
