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

import javax.jms.MessageConsumer;

import org.eclipse.ditto.connectivity.model.Source;

/**
 * Wraps associated data of a JMS message consumer.
 */
public final class ConsumerData {

    private final Source source;
    private final String address;
    private final String addressWithIndex;
    private final MessageConsumer messageConsumer;

    private ConsumerData(final Source source, final String address, final String addressWithIndex,
            final MessageConsumer messageConsumer) {
        this.source = source;
        this.address = address;
        this.addressWithIndex = addressWithIndex;
        this.messageConsumer = messageConsumer;
    }

    static ConsumerData of(final Source source, final String address, final String addressWithIndex,
            final MessageConsumer messageConsumer) {
        return new ConsumerData(source, address, addressWithIndex, messageConsumer);
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

    ConsumerData withMessageConsumer(final MessageConsumer messageConsumer) {
        return new ConsumerData(source, address, addressWithIndex, messageConsumer);
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
