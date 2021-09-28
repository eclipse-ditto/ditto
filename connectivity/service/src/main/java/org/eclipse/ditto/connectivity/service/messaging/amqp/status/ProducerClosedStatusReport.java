/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.amqp.status;

import java.util.Objects;

import javax.jms.MessageProducer;

/**
 * Report about closed producer.
 */
public final class ProducerClosedStatusReport {

    private final MessageProducer messageProducer;
    private final Throwable cause;

    private ProducerClosedStatusReport(final MessageProducer messageProducer, final Throwable cause) {
        this.messageProducer = messageProducer;
        this.cause = cause;
    }

    public static ProducerClosedStatusReport get(final MessageProducer messageProducer, final Throwable cause) {
        return new ProducerClosedStatusReport(messageProducer, cause);
    }

    /**
     * @return the closed message producer
     */
    public MessageProducer getMessageProducer() {
        return messageProducer;
    }

    /**
     * @return the cause why the producer was closed
     */
    public Throwable getCause() {
        return cause;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProducerClosedStatusReport that = (ProducerClosedStatusReport) o;
        return Objects.equals(messageProducer, that.messageProducer) &&
                Objects.equals(cause, that.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageProducer, cause);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "messageProducer=" + messageProducer +
                ", cause=" + cause +
                "]";
    }
}
