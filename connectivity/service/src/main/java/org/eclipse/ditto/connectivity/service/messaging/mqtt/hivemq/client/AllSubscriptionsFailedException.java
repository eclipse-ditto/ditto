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

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;

/**
 * This is exception is thrown to indicate that all subscriptions of an MQTT Subscribe message
 * ({@link GenericMqttSubscribe}) failed.
 */
public final class AllSubscriptionsFailedException extends SubscriptionsFailedException {

    @Serial private static final long serialVersionUID = 4004096047016339725L;

    /**
     * Constructs a new {@code AllSubscriptionsFailedException} object.
     *
     * @param failedSubscriptionStatuses a List containing the status of each failed subscription.
     * @param cause the cause of the exception or {@code null} if unknown.
     * @throws NullPointerException if {@code failedSubscriptionStatuses} is {@code null}.
     * @throws IllegalArgumentException if {@code failedSubscriptionStatuses} is empty.
     */
    public AllSubscriptionsFailedException(final List<SubscriptionStatus> failedSubscriptionStatuses,
            @Nullable final Throwable cause) {

        super(failedSubscriptionStatuses, cause);
    }

}
