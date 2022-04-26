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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import javax.annotation.concurrent.Immutable;

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

/**
 * Status of a single subscription of a sent Subscribe message ({@link GenericMqttSubscribe}).
 * This type associates a {@link MqttTopicFilter} with its {@link GenericMqttSubAckStatus}.
 * The SubAck message status indicates whether the subscription to the topic filter was successful or if it failed with
 * an error.
 */
@Immutable
public record SubscriptionStatus(MqttTopicFilter mqttTopicFilter,
                          GenericMqttSubAckStatus genericMqttSubAckStatus) {}
