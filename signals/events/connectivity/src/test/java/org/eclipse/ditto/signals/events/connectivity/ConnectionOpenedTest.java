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
package org.eclipse.ditto.signals.events.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ConnectionOpened}.
 */
public final class ConnectionOpenedTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Event.JsonFields.TYPE, ConnectionOpened.TYPE)
            .set(ConnectivityEvent.JsonFields.CONNECTION_ID, TestConstants.ID)
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ConnectionOpened.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ConnectionOpened.class, areImmutable());
    }

    @Test
    public void createInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConnectionOpened.of(null, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Connection ID")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final ConnectionOpened expected = ConnectionOpened.of(TestConstants.ID, DittoHeaders.empty());

        final ConnectionOpened actual = ConnectionOpened.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = ConnectionOpened.of(TestConstants.ID, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

}
