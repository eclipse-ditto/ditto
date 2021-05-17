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

import org.eclipse.ditto.connectivity.model.Source;

/**
 * This is class holds the data to identify a single address of a source of a kafka connection.
 */
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

}
