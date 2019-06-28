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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import javax.jms.MessageConsumer;

class ConsumerClosedStatusReport {

    private final MessageConsumer messageConsumer;

    private ConsumerClosedStatusReport(final MessageConsumer messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    static ConsumerClosedStatusReport get(final MessageConsumer messageConsumer) {
        return new ConsumerClosedStatusReport(messageConsumer);
    }

    MessageConsumer getMessageConsumer() {
        return messageConsumer;
    }
}
