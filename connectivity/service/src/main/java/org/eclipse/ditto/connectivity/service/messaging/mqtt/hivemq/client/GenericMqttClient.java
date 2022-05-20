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

}
