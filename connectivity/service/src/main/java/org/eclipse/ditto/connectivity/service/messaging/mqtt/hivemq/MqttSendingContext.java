/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.messaging.SendResult;

final class MqttSendingContext<P> {

    private final P mqttMessage;
    private final Signal<?> signal;
    private final CompletableFuture<SendResult> sendResult;
    private final ExternalMessage message;
    @Nullable private final Target autoAckTarget;

    MqttSendingContext(final P mqttMessage,
            final Signal<?> signal,
            final CompletableFuture<SendResult> sendResult,
            final ExternalMessage message,
            @Nullable final Target autoAckTarget) {

        this.mqttMessage = mqttMessage;
        this.signal = signal;
        this.sendResult = sendResult;
        this.message = message;
        this.autoAckTarget = autoAckTarget;
    }

    P getMqttMessage() {
        return mqttMessage;
    }

    Signal<?> getSignal() {
        return signal;
    }

    @Nullable
    Target getAutoAckTarget() {
        return autoAckTarget;
    }

    CompletableFuture<SendResult> getSendResult() {
        return sendResult;
    }

    ExternalMessage getMessage() {
        return message;
    }
}
