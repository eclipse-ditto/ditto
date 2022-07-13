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

import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedContext;

/**
 * Listener which is notified when a client is connected to a broker.
 */
@FunctionalInterface
public interface GenericMqttClientConnectedListener {

    /**
     * Listener method that is notified when a client is connected to a broker.
     * <em>This method must not block.</em>

     * @param context context about the client that is now connected.
     * @param clientRole role of the client that is now connected.
     */
    void onConnected(MqttClientConnectedContext context, ClientRole clientRole);

}
