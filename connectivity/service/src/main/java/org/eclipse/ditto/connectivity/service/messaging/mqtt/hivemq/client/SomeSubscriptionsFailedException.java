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

import java.io.Serial;
import java.util.List;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;

/**
 * This is exception is thrown to indicate that some subscriptions of an MQTT Subscribe message
 * ({@link GenericMqttSubscribe}) failed while other subscriptions were successful.
 */
public final class SomeSubscriptionsFailedException extends SubscriptionsFailedException {

    @Serial private static final long serialVersionUID = -3915763304324220251L;

    /**
     * Constructs a new {@code SomeSubscriptionsFailedException} object.
     *
     * @param failedSubscriptionStatuses a List containing the status of each failed subscription.
     * @throws NullPointerException if {@code failedSubscriptionStatuses} is {@code null}.
     * @throws IllegalArgumentException if {@code failedSubscriptionStatuses} is empty.
     */
    public SomeSubscriptionsFailedException(final List<SubscriptionStatus> failedSubscriptionStatuses) {
        super(failedSubscriptionStatuses, null);
    }

}
