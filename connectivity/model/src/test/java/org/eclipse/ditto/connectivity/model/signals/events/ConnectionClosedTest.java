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
package org.eclipse.ditto.connectivity.model.signals.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ConnectionClosed}.
 */
public final class ConnectionClosedTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Event.JsonFields.TYPE, ConnectionClosed.TYPE)
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.REVISION)
            .set(ConnectivityEvent.JsonFields.CONNECTION_ID, TestConstants.ID.toString())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ConnectionClosed.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ConnectionClosed.class, areImmutable());
    }

    @Test
    public void createInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConnectionClosed.of(null, TestConstants.REVISION,
                        TestConstants.TIMESTAMP, TestConstants.HEADERS, TestConstants.METADATA))
                .withMessage("The %s must not be null!", "Connection ID")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final ConnectionClosed expected = ConnectionClosed.of(TestConstants.ID, TestConstants.REVISION,
                TestConstants.TIMESTAMP, TestConstants.HEADERS, TestConstants.METADATA);

        final ConnectionClosed actual = ConnectionClosed.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = ConnectionClosed.of(TestConstants.ID, TestConstants.REVISION,
                TestConstants.TIMESTAMP, TestConstants.HEADERS, TestConstants.METADATA).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = JsonFactory.emptyPointer();

        final ConnectionClosed underTest =
                ConnectionClosed.of(TestConstants.ID, TestConstants.REVISION,
                        TestConstants.TIMESTAMP, TestConstants.HEADERS, TestConstants.METADATA);

        DittoJsonAssertions.assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
