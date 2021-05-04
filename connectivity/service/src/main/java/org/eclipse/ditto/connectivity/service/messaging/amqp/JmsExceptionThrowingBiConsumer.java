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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import java.util.function.BiConsumer;

import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;

/**
 * An interface similar to BiConsumer that accepts a JMS Message and a String and throws {@link JMSException}s.
 *
 * @param <T> type of the second argument.
 */
@FunctionalInterface
public interface JmsExceptionThrowingBiConsumer<T> {

    /**
     * Performs this operation on the given arguments of type JMS Message and String.
     *
     * @param message the JMS message to accept.
     * @param value the String to accept.
     * @throws JMSException when the underlying JMS implementation raised a JMSException
     */
    void accept(Message message, T value) throws JMSException;

    /**
     * Wraps a {@link JmsExceptionThrowingBiConsumer} returning a BiConsumer by converting thrown {@link JMSException}s
     * to {@link JMSRuntimeException}s.
     *
     * @param throwingConsumer the JmsExceptionThrowingBiConsumer that throws {@link JMSException}
     * @return a BiConsumer that throws {@link JMSRuntimeException}
     */
    static <T> BiConsumer<Message, T> wrap(final JmsExceptionThrowingBiConsumer<T> throwingConsumer) {
        return (m, v) -> {
            try {
                throwingConsumer.accept(m, v);
            } catch (final JMSException jmsException) {
                throw new JMSRuntimeException(jmsException.getMessage(), jmsException.getErrorCode(),
                        jmsException.getCause());
            }
        };
    }
}
