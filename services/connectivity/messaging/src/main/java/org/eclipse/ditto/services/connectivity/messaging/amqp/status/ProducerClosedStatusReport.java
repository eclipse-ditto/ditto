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
package org.eclipse.ditto.services.connectivity.messaging.amqp.status;

import javax.jms.MessageProducer;

/**
 * Report about closed producer.
 */
public final class ProducerClosedStatusReport {

    private final MessageProducer messageProducer;

    private ProducerClosedStatusReport(final MessageProducer messageProducer) {
        this.messageProducer = messageProducer;
    }

    public static ProducerClosedStatusReport get(final MessageProducer messageProducer) {
        return new ProducerClosedStatusReport(messageProducer);
    }

    public MessageProducer getMessageProducer() {
        return messageProducer;
    }
}
