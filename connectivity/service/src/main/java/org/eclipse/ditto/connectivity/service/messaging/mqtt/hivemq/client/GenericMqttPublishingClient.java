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

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;

/**
 * A generic client for sending {@link GenericMqttPublish} messages to the broker.
 */
public interface GenericMqttPublishingClient {

    /**
     * Sends the specified {@code GenericMqttPublish} message to the broker.
     *
     * @param genericMqttPublish the Publish message sent to the broker.
     * @return a {@code CompletionStage} which always completes normally with a {@link GenericMqttPublishResult}.
     * If an error occurred before the Publish message was sent or before an acknowledgement message was received,
     * the yielded {@code GenericMqttPublishResult} represents a failure and provides the occurred error. I.e. all
     * known possible exceptions are moved in-band to make it easier to handle and reason about the returned
     * CompletionStage.
     * @throws NullPointerException if {@code genericMqttPublish} is {@code null}.
     */
    CompletionStage<GenericMqttPublishResult> publish(GenericMqttPublish genericMqttPublish);

}
