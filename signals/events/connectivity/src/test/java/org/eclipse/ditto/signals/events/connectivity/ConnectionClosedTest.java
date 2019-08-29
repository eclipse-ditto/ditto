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
package org.eclipse.ditto.signals.events.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ConnectionClosed}.
 */
public final class ConnectionClosedTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Event.JsonFields.TYPE, ConnectionClosed.TYPE)
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
    public void createInstanceWithNullConnectionIdString() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConnectionClosed.of((String) null, DittoHeaders.empty()))
                .withMessage("The ID must not be null!")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConnectionClosed.of((ConnectionId) null, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Connection ID")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final ConnectionClosed expected = ConnectionClosed.of(TestConstants.ID, DittoHeaders.empty());

        final ConnectionClosed actual = ConnectionClosed.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = ConnectionClosed.of(TestConstants.ID, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = JsonFactory.emptyPointer();

        final ConnectionClosed underTest =
                ConnectionClosed.of(TestConstants.ID, DittoHeaders.empty());

        DittoJsonAssertions.assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
