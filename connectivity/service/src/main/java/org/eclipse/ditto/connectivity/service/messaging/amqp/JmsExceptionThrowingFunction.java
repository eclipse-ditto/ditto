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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import java.util.function.Function;

import javax.annotation.Nullable;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;

/**
 * An interface similar to Function that accepts a JMS message and returns an optional string and throws a
 * {@link jakarta.jms.JMSException}.
 *
 * @param <T> type of results.
 */
@FunctionalInterface
public interface JmsExceptionThrowingFunction<T> {

    /**
     * Performs this operation on the given argument of type JMS Message.
     *
     * @param message the JMS message argument.
     * @return the result.
     * @throws jakarta.jms.JMSException when the underlying JMS implementation raised a JMSException
     */
    @Nullable
    T apply(Message message) throws JMSException;

    /**
     * Wraps a {@link JmsExceptionThrowingFunction} returning
     * a function by converting thrown {@link jakarta.jms.JMSException} to {@link jakarta.jms.JMSRuntimeException}.
     *
     * @param throwingFunction the JmsExceptionThrowingBiConsumer that throws {@link jakarta.jms.JMSException}
     * @param <T> type of results.
     * @return a function that throws {@link jakarta.jms.JMSRuntimeException}
     */
    static <T> Function<Message, T> wrap(final JmsExceptionThrowingFunction<T> throwingFunction) {
        return m -> {
            try {
                return throwingFunction.apply(m);
            } catch (final JMSException jmsException) {
                throw new JMSRuntimeException(jmsException.getMessage(), jmsException.getErrorCode(),
                        jmsException.getCause());
            }
        };
    }
}
