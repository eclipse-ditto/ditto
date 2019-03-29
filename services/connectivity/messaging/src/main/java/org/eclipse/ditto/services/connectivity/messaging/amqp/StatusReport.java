/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import java.util.Optional;
import javax.annotation.Nullable;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;

/**
 * Message for status reporter.
 */
final class StatusReport {

    private final boolean consumedMessage;
    private final boolean connectionRestored;
    @Nullable private final ConnectionFailure failure;
    @Nullable private final MessageConsumer closedConsumer;
    @Nullable private final MessageProducer closedProducer;

    private StatusReport(
            final boolean consumedMessage,
            final boolean connectionRestored,
            @Nullable final ConnectionFailure failure,
            @Nullable final MessageConsumer closedConsumer,
            @Nullable final MessageProducer closedProducer) {
        this.consumedMessage = consumedMessage;
        this.connectionRestored = connectionRestored;
        this.failure = failure;
        this.closedConsumer = closedConsumer;
        this.closedProducer = closedProducer;
    }

    static StatusReport connectionRestored() {
        return new StatusReport(false, true, null, null, null);
    }

    static StatusReport failure(final ConnectionFailure failure) {
        return new StatusReport(false, false, failure, null, null);
    }

    static StatusReport consumedMessage() {
        return new StatusReport(true, false, null, null, null);
    }

    static StatusReport consumerClosed(final MessageConsumer consumer) {
        return new StatusReport(false, false, null, consumer, null);
    }

    static StatusReport producerClosed(final MessageProducer producer) {
        return new StatusReport(false, false, null, null, producer);
    }

    private boolean hasConsumedMessage() {
        return consumedMessage;
    }

    boolean isConnectionRestored() {
        return connectionRestored;
    }

    Optional<ConnectionFailure> getFailure() {
        return Optional.ofNullable(failure);
    }

    Optional<MessageConsumer> getClosedConsumer() {
        return Optional.ofNullable(closedConsumer);
    }

    Optional<MessageProducer> getClosedProducer() {
        return Optional.ofNullable(closedProducer);
    }
}