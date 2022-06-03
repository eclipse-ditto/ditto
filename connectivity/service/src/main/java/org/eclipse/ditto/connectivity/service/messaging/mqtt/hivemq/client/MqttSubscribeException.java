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

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;

/**
 * This exception is thrown to indicate that subscribing a client via one or multiple Subscribe messages
 * ({@link GenericMqttSubscribe}) failed for some reason.
 */
public sealed class MqttSubscribeException extends RuntimeException permits SubscriptionsFailedException {

    @Serial private static final long serialVersionUID = 1936431587204652088L;

    /**
     * Constructs a {@code MqttSubscribeException} object for the specified String and Throwable argument.
     *
     * @param detailMessage the detail message of the exception.
     * @param cause the Throwable that caused the constructed exception or {@code null} if the cause cannot be
     * determined.
     * @throws NullPointerException if {@code failedSubscriptionStatuses} is {@code null}.
     * @throws IllegalArgumentException if {@code failedSubscriptionStatuses} is empty.
     */
    public MqttSubscribeException(final String detailMessage, @Nullable final Throwable cause) {
        super(detailMessage, cause);
    }

    /**
     * Constructs a {@code MqttSubscribeException} object.
     */
    public MqttSubscribeException() {
        super();
    }

    /**
     * Constructs a {@code MqttSubscribeException} object for the specified Throwable argument.
     * The detail message is taken over from the argument.
     *
     * @param cause the Throwable that caused the constructed exception.
     * @throws NullPointerException if {@code failedSubscriptionStatuses} is {@code null}.
     * @throws IllegalArgumentException if {@code failedSubscriptionStatuses} is empty.
     */
    public MqttSubscribeException(@Nullable final Throwable cause) {
        super(null != cause ? cause.getMessage() : null, cause);
    }

}

