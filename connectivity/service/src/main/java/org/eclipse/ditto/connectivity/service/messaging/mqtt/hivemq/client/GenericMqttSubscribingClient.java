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

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAck;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;

import com.hivemq.client.rx.FlowableWithSingle;

/**
 * Generic client for subscribing to topics at the MQTT broker and consuming Publish messages for the subscribed topic
 * from the broker.
 */
public interface GenericMqttSubscribingClient {

    /**
     * Creates a {@link FlowableWithSingle} for consuming all incoming Publish messages matching the passed
     * {@code genericMqttSubscribe} subscription.
     * <em>The Publish messages have to be acknowledged manually.</em>
     *
     * @param genericMqttSubscribe provides the Subscribe message sent to the broker declaring to which topics to
     * subscribe.
     * @return the {@code FlowableWithSingle} which
     * <ul>
     *     <li>
     *         emits the incoming Publish messages matching
     *         {@link com.hivemq.client.mqtt.MqttGlobalPublishFilter#SUBSCRIBED MqttGlobalPublishFilter.SUBSCRIBED},
     *     </li>
     *     <li>never completes but</li>
     *     <li>
     *         errors with an
     *         {@link com.hivemq.client.mqtt.exceptions.MqttSessionExpiredException MqttSessionExpiredException} when
     *         the MQTT session expires.
     *     </li>
     * </ul>
     * And the {@code Single} part which:
     * <ul>
     *     <li>
     *         succeeds with the generic MQTT SubAck message if at least one subscription of the Subscribe message was
     *         successful (the SubAck message contains no SubAck status that is an error),
     *     </li>
     *     <li>
     *         errors with an {@link AllSubscriptionsFailedException} wrapping the failed {@link SubscriptionStatus}es
     *         if SubAck message only contains error codes,
     *     </li>
     *     <li>
     *         errors with a {@link SomeSubscriptionsFailedException} wrapping the failed {@link SubscriptionStatus}es
     *         if SubAck messages contains error codes for some subscriptions or
     *     </li>
     *     <li>
     *         errors with a {@link MqttSubscribeException} wrapping the cause exception if an error occurred before
     *         the Subscribe message was sent or before a SubAck message was received.
     *     </li>
     * </ul>
     */
    FlowableWithSingle<GenericMqttPublish, GenericMqttSubAck> subscribePublishesWithManualAcknowledgement(
            GenericMqttSubscribe genericMqttSubscribe
    );

}
