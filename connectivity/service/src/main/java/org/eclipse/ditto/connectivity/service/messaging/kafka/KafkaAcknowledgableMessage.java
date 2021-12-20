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
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;

import akka.kafka.ConsumerMessage;

/**
 * Wraps an {@link AcknowledgeableMessage} and provides a future of {@link akka.kafka.ConsumerMessage.CommittableOffset}
 * which is completed:
 * <ul>
 * <li><em>successfully</em> once the requested acknowledgement was issued/settled</li>
 * <li>with a {@link MessageRejectedException} when the requested acknowledgement could not be issued and redelivery
 * of the message is required</li>
 * </ul>
 */
@Immutable
final class KafkaAcknowledgableMessage {

    private final AcknowledgeableMessage acknowledgeableMessage;
    private final CompletableFuture<ConsumerMessage.CommittableOffset> acknowledgementFuture;

    KafkaAcknowledgableMessage(final ExternalMessage message,
            final ConsumerMessage.CommittableOffset committableOffset,
            final ConnectionMonitor ackMonitor) {
        this.acknowledgementFuture = new CompletableFuture<>();
        this.acknowledgeableMessage = AcknowledgeableMessage.of(message,
                () -> {
                    ackMonitor.success(message);
                    acknowledgementFuture.complete(committableOffset);
                },
                shouldRedeliver -> {
                    if (shouldRedeliver) {
                        ackMonitor.exception(message, "Message was rejected and redelivery is requested");
                        acknowledgementFuture.completeExceptionally(MessageRejectedException.getInstance());
                    } else {
                        ackMonitor.exception(message, "Message was rejected and no redelivery is requested");
                        acknowledgementFuture.complete(committableOffset);
                    }
                });
    }

    AcknowledgeableMessage getAcknowledgeableMessage() {
        return acknowledgeableMessage;
    }

    CompletableFuture<ConsumerMessage.CommittableOffset> getAcknowledgementFuture() {
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
        final KafkaAcknowledgableMessage that = (KafkaAcknowledgableMessage) o;
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
