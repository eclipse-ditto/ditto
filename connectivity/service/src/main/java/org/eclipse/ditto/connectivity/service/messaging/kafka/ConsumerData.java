/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.Source;

/**
 * This is class holds the data to identify a single address of a source of a Kafka connection.
 */
@Immutable
final class ConsumerData {

    private final Source source;
    private final String address;
    private final String addressWithIndex;

    ConsumerData(final Source source, final String address, final String addressWithIndex) {
        this.source = source;
        this.address = address;
        this.addressWithIndex = addressWithIndex;
    }

    Source getSource() {
        return source;
    }

    String getAddress() {
        return address;
    }

    String getActorNamePrefix() {
        return KafkaConsumerActor.ACTOR_NAME_PREFIX + source.getIndex() + "-" + addressWithIndex;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConsumerData that = (ConsumerData) o;
        return Objects.equals(source, that.source) && Objects.equals(address, that.address) &&
                Objects.equals(addressWithIndex, that.addressWithIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, address, addressWithIndex);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "source=" + source +
                ", address=" + address +
                ", addressWithIndex=" + addressWithIndex +
                "]";
    }
}
