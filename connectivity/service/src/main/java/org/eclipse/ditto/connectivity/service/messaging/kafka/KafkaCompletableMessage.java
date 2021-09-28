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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.service.messaging.AcknowledgeableMessage;

import akka.Done;

/**
 * Wraps an {@link org.eclipse.ditto.connectivity.service.messaging.AcknowledgeableMessage} and provides a future of {@link akka.Done}
 * which is completed successfully in all cases:
 * <ul>
 * <li>once the requested acknowledgement was issued/settled</li>
 * <li>once the requested acknowledgement was rejected but not with redelivery request</li>
 * <li>once the requested acknowledgement was rejected with redelivery request</li>
 * </ul>
 * Its purpose is to know when a message has been fully processed.
 */
@Immutable
final class KafkaCompletableMessage {

    private final AcknowledgeableMessage acknowledgeableMessage;
    private final CompletableFuture<Done> acknowledgementFuture;

    KafkaCompletableMessage(final ExternalMessage message) {
        this.acknowledgementFuture = new CompletableFuture<>();
        this.acknowledgeableMessage = AcknowledgeableMessage.of(message,
                () -> acknowledgementFuture.complete(Done.getInstance()),
                shouldRedeliver -> acknowledgementFuture.complete(Done.getInstance()));
    }

    AcknowledgeableMessage getAcknowledgeableMessage() {
        return acknowledgeableMessage;
    }

    CompletableFuture<Done> getAcknowledgementFuture() {
        return acknowledgementFuture;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KafkaCompletableMessage that = (KafkaCompletableMessage) o;
        return Objects.equals(acknowledgeableMessage, that.acknowledgeableMessage) &&
                Objects.equals(acknowledgementFuture, that.acknowledgementFuture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acknowledgeableMessage, acknowledgementFuture);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "acknowledgeableMessage=" + acknowledgeableMessage +
                ", acknowledgementFuture=" + acknowledgementFuture +
                "]";
    }

}
