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

/**
 * This package provides a generic type for an MQTT Publish message
 * (see {@link org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish}) to subsume
 * the disjoint MQTT Publish message implementations for protocol version 3 and 5, that are provided by HiveMQ client
 * API.
 * Having a generic type for an MQTT Publish has the advantage that processing logic exists only once.
 * The generic type is a very thin wrapper for the protocol version specific types provided by HiveMQ client API.
 * <p>
 * Additional content of this package are types that are required for transforming a generic MQTT Publish to an
 * {@link org.eclipse.ditto.connectivity.api.ExternalMessage} or vice versa.
 * The actual transformation logic implementation is located where it is used, i.e. in {@code consuming} and
 * {@code publishing} package.
 */
@org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish;
