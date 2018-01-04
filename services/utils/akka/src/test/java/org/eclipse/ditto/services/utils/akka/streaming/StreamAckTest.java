/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akka.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Test for {@link org.eclipse.ditto.services.utils.akka.streaming.StreamAck}.
 */
public class StreamAckTest {

    private static final String KNOWN_ELEMENT_ID = "knownElementId";

    @Test
    public void assertImmutability() {
        assertInstancesOf(StreamAck.class, areImmutable());
    }

    @Test
    public void equalsAndHashcode() throws Exception {
        EqualsVerifier.forClass(StreamAck.class).verify();
    }

    @Test
    public void success() {
        final StreamAck ack = StreamAck.success(KNOWN_ELEMENT_ID);
        assertThat(ack.getElementId()).isEqualTo(KNOWN_ELEMENT_ID);
        assertThat(ack.getStatus()).isEqualTo(StreamAck.Status.SUCCESS);
    }

    @Test
    public void failure() {
        final StreamAck ack = StreamAck.failure(KNOWN_ELEMENT_ID);
        assertThat(ack.getElementId()).isEqualTo(KNOWN_ELEMENT_ID);
        assertThat(ack.getStatus()).isEqualTo(StreamAck.Status.FAILURE);
    }

    @Test
    public void toJson() {
        final StreamAck original = StreamAck.success(KNOWN_ELEMENT_ID);
        final JsonValue serialized = original.toJson();
        final StreamAck deserialized = StreamAck.fromJson(JsonFactory.newObject(serialized.toString()));
        assertThat(deserialized)
                .isEqualTo(original);
    }

}