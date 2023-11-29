/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;

/**
 * Represents an MQTT client that can consume publishes from a broker. The client
 * buffers messages until {@code stopBufferingPublishes} is called.
 * This interface abstracts MQTT protocol version 3 and 5.
 */
interface GenericMqttConsumingClient extends Disposable {

    /**
     * Consumes all publishes.
     * If buffering is enabled, the flowable returns all buffered publishes as well.
     *
     * @return the {@code Flowable} which emits all publishes.
     */
    Flowable<GenericMqttPublish> consumePublishes();

    /**
     * Stops buffering publishes. All new subscribers that use {@code consumePublishes}
     * method will get only new publishes.
     */
    void stopBufferingPublishes();

}
