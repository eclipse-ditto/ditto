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

import javax.jms.MessageConsumer;

import org.eclipse.ditto.model.connectivity.Source;

/**
 * Wraps associated data of a JMS message consumer.
 */
public final class ConsumerData {

    private final Source source;
    private final String address;
    private final String addressWithIndex;
    private final MessageConsumer messageConsumer;

    ConsumerData(final Source source, final String address, final String addressWithIndex,
            final MessageConsumer messageConsumer) {
        this.source = source;
        this.address = address;
        this.addressWithIndex = addressWithIndex;
        this.messageConsumer = messageConsumer;
    }

    Source getSource() {
        return source;
    }

    String getAddress() {
        return address;
    }

    String getAddressWithIndex() {
        return addressWithIndex;
    }

    MessageConsumer getMessageConsumer() {
        return messageConsumer;
    }

    String getActorNamePrefix() {
        return AmqpConsumerActor.ACTOR_NAME_PREFIX + source.getIndex() + "-" + addressWithIndex;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ConsumerData wrapper = (ConsumerData) o;
        return Objects.equals(source, wrapper.source) &&
                Objects.equals(address, wrapper.address) &&
                Objects.equals(addressWithIndex, wrapper.addressWithIndex) &&
                Objects.equals(messageConsumer, wrapper.messageConsumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, address, addressWithIndex, messageConsumer);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "address=" + address +
                ", addressWithIndex=" + addressWithIndex +
                "]";
    }
}
