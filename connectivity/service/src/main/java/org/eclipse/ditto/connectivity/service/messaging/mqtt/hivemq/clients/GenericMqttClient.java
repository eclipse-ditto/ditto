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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients;

import java.util.concurrent.CompletionStage;

/**
 * An MQTT client which can be connected with and disconnected from a broker.
 */
public interface GenericMqttClient {

    /**
     * Connects this client with the specified Connect message.

     * @param genericMqttConnect the Connect message to send to the broker.
     * @return a {@code CompletionStage} which
     * <ul>
     *     <li>completes with the ConnAck message if it does not contain an Error Code (connected successfully),</li>
     *     <li>
     *         completes exceptionally with a {@link MqttClientConnectException} if the ConnAck message contains an
     *         Error Code or
     *     </li>
     *     <li>
     *         completes exceptionally with a different exception if an error occurred before the Connect message was
     *         sent or before the ConnAck message was received.
     *     </li>
     * </ul>
     * @throws NullPointerException if {@code genericMqttConnect} is {@code null}.
     */
    CompletionStage<GenericMqttConnAckStatus> connect(GenericMqttConnect genericMqttConnect);

    /**
     * Disconnects this client.
     *
     * @return a CompletionStage which
     * <ul>
     *     <li>completes when the client was successfully disconnected or</li>
     *     <li>completes exceptionally if the client did not disconnect gracefully</li>
     * </ul>
     */
    CompletionStage<Void> disconnect();

}
