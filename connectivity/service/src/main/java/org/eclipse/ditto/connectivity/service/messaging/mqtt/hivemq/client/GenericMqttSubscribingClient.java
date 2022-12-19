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
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAck;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * Generic client for subscribing to topics at the MQTT broker and consuming Publish messages for the subscribed topic
 * from the broker.
 */
public interface GenericMqttSubscribingClient {

    /**
     * Subscribes this client with the specified {@code GenericMqttSubscribe} message.
     * <p>
     * The returned {@code Single} represents the source of the SubAck message corresponding to the given Subscribe
     * message.
     * Calling this method does not subscribe yet.
     * Subscribing is performed lazily and asynchronously when subscribing (in terms of Reactive Streams) to the
     * returned {@code Single}.
     *
     * @param genericMqttSubscribe provides the Subscribe message sent to the broker during subscribe
     * @return a {@code Single} which
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
     * @see #consumeSubscribedPublishesWithManualAcknowledgement() for consuming the incoming Publish messages for the
     * subscribed topics.
     */
    Single<GenericMqttSubAck> subscribe(GenericMqttSubscribe genericMqttSubscribe);

    /**
     * Creates a {@link Flowable} for globally consuming all incoming Publish messages resulting from subscriptions
     * made by the client.
     *
     * <em>The Publish messages have to be acknowledged manually.</em>
     *
     * @return the {@code Flowable} which
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
     * @see #subscribe(GenericMqttSubscribe)
     */
    Flowable<GenericMqttPublish> consumeSubscribedPublishesWithManualAcknowledgement();

    /**
     * Unsubscribe from the topic filters.
     *
     * @param mqttTopicFilters The topic filters.
     * @return the {@code Completable} that completes or fails according to the unsubscription result.
     */
    CompletionStage<Void> unsubscribe(MqttTopicFilter... mqttTopicFilters);
}
