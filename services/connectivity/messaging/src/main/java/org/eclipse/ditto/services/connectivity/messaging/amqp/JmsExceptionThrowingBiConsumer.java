/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import java.util.function.BiConsumer;

import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;

import org.apache.qpid.jms.message.JmsMessage;

/**
 * A BiConsumer that throws {@link JMSException}s.
 */
@FunctionalInterface
public interface JmsExceptionThrowingBiConsumer { void accept(Message message, String value) throws JMSException;

    /**
     * Converts {@link JMSException} to {@link JMSRuntimeException}
     * @param throwingConsumer the consumer that throws {@link JMSException}
     * @return a consumer that throws {@link JMSRuntimeException}
     */
    static BiConsumer<Message, String> wrap(JmsExceptionThrowingBiConsumer throwingConsumer) {
        return (m,v) -> {
            try {
                throwingConsumer.accept(m, v);
            } catch (JMSException jmsException) {
                throw new JMSRuntimeException(jmsException.getMessage(), jmsException.getErrorCode(), jmsException.getCause());
            }
        };
    }
}
