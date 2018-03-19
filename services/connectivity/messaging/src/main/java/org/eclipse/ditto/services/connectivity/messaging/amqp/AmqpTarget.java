/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import java.util.Objects;

import javax.jms.Destination;

import org.apache.qpid.jms.JmsQueue;
import org.eclipse.ditto.services.connectivity.messaging.PublishTarget;

/**
 * An {@link AmqpTarget} holds a JMS {@link Destination}.
 */
public class AmqpTarget implements PublishTarget {

    private final Destination jmsDestination;

    private AmqpTarget(final Destination jmsDestination) {this.jmsDestination = jmsDestination;}

    static AmqpTarget fromTarget(final String target) {
        return new AmqpTarget(new JmsQueue(target));
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
