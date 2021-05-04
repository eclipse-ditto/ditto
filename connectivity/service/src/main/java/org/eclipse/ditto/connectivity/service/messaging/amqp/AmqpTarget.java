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

import java.util.Objects;

import javax.jms.Destination;

import org.apache.qpid.jms.JmsQueue;
import org.apache.qpid.jms.JmsTopic;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.service.messaging.PublishTarget;

/**
 * An {@link AmqpTarget} holds a JMS {@link Destination}.
 */
public final class AmqpTarget implements PublishTarget {

    private static final String TOPIC_PREFIX = "topic://";
    private static final String QUEUE_PREFIX = "queue://";
    private static final String EMPTY_STRING = "";
    private final Destination jmsDestination;

    private AmqpTarget(final Destination jmsDestination) {this.jmsDestination = jmsDestination;}

    static AmqpTarget fromTargetAddress(final String targetAddress) {
        ConditionChecker.argumentNotEmpty(targetAddress, "targetAddress");
        final Destination destination;
        if (targetAddress.startsWith(TOPIC_PREFIX)) {
            destination = new JmsTopic(targetAddress.replace(TOPIC_PREFIX, EMPTY_STRING));
        } else if (targetAddress.startsWith(QUEUE_PREFIX)) {
            destination = new JmsQueue(targetAddress.replace(QUEUE_PREFIX, EMPTY_STRING));
        } else {
            destination = new JmsQueue(targetAddress);
        }
        return new AmqpTarget(destination);
    }

    Destination getJmsDestination() {
        return jmsDestination;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final AmqpTarget that = (AmqpTarget) o;
        return Objects.equals(jmsDestination, that.jmsDestination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jmsDestination);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "jmsDestination=" + jmsDestination +
                "]";
    }
}
