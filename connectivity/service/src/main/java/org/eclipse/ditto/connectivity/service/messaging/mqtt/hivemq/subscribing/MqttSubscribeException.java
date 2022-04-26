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

import java.io.Serial;

/**
 * This exception is thrown to indicate that subscribing a client via Subscribe message ({@link GenericMqttSubscribe})
 * failed for some reason.
 */
public sealed class MqttSubscribeException extends RuntimeException
        permits AllSubscriptionsFailedException, SomeSubscriptionsFailedException {

    @Serial private static final long serialVersionUID = 1936431587204652088L;

    /**
     * Constructs a {@code MqttSubscribeException} object.
     */
    MqttSubscribeException() {
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
    MqttSubscribeException(final Throwable cause) {
        super(cause.getMessage(), cause);
    }

    /**
     * Constructs a {@code MqttSubscribeException} object for the specified String and Throwable argument.
     *
     * @param detailMessage the detail message of the exception.
     * @param cause the Throwable that caused the constructed exception.
     * @throws NullPointerException if {@code failedSubscriptionStatuses} is {@code null}.
     * @throws IllegalArgumentException if {@code failedSubscriptionStatuses} is empty.
     */
    MqttSubscribeException(final String detailMessage, final Throwable cause) {
        super(detailMessage, cause);
    }

}

