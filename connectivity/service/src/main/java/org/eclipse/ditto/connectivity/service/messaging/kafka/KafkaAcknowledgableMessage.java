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

import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.service.messaging.AcknowledgeableMessage;

import akka.kafka.ConsumerMessage;

final class KafkaAcknowledgableMessage {

    private final AcknowledgeableMessage acknowledgeableMessage;
    private final CompletableFuture<ConsumerMessage.CommittableOffset> acknowledgementFuture;

    KafkaAcknowledgableMessage(final ExternalMessage message,
            final ConsumerMessage.CommittableOffset committableOffset) {
        this.acknowledgementFuture = new CompletableFuture<>();
        this.acknowledgeableMessage = AcknowledgeableMessage.of(message,
                () -> acknowledgementFuture.complete(committableOffset),
                shouldRedeliver -> {
                    if (shouldRedeliver) {
                        acknowledgementFuture.completeExceptionally(MessageRejectedException.getInstance());
                    } else {
                        acknowledgementFuture.complete(committableOffset);
                    }
                });
    }

    private KafkaAcknowledgableMessage(final AcknowledgeableMessage acknowledgeableMessage,
            final CompletableFuture<ConsumerMessage.CommittableOffset> acknowledgementFuture) {
        this.acknowledgementFuture = acknowledgementFuture;
        this.acknowledgeableMessage = acknowledgeableMessage;
    }

    AcknowledgeableMessage getAcknowledgeableMessage() {
        return acknowledgeableMessage;
    }

    CompletableFuture<ConsumerMessage.CommittableOffset> getAcknowledgementFuture() {
        return acknowledgementFuture;
    }

    KafkaAcknowledgableMessage commitAfter(final CompletableFuture<ConsumerMessage.CommittableOffset> precedingFuture) {
        final CompletableFuture<ConsumerMessage.CommittableOffset> chainedFuture =
                precedingFuture.thenCompose(result -> acknowledgementFuture);
        return new KafkaAcknowledgableMessage(acknowledgeableMessage, chainedFuture);
    }

}
