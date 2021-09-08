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

import javax.jms.MessageConsumer;

/**
 * Report about closed consumer.
 */
public final class ConsumerClosedStatusReport {

    private final MessageConsumer messageConsumer;
    private final Throwable cause;

    private ConsumerClosedStatusReport(final MessageConsumer messageConsumer, final Throwable cause) {
        this.messageConsumer = messageConsumer;
        this.cause = cause;
    }

    public static ConsumerClosedStatusReport get(final MessageConsumer messageConsumer, final Throwable cause) {
        return new ConsumerClosedStatusReport(messageConsumer, cause);
    }

    /**
     * @return the closed message consumer
     */
    public MessageConsumer getMessageConsumer() {
        return messageConsumer;
    }

    /**
     * @return the cause why the consumer was closed
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
        final ConsumerClosedStatusReport that = (ConsumerClosedStatusReport) o;
        return Objects.equals(messageConsumer, that.messageConsumer) &&
                Objects.equals(cause, that.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageConsumer, cause);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "messageConsumer=" + messageConsumer +
                ", cause=" + cause +
                "]";
    }
}
