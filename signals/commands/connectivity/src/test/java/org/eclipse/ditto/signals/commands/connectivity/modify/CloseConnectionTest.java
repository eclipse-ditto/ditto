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
package org.eclipse.ditto.signals.commands.connectivity.modify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link CloseConnection}.
 */
public final class CloseConnectionTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Command.JsonFields.TYPE, CloseConnection.TYPE)
            .set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, TestConstants.ID)
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CloseConnection.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(CloseConnection.class, areImmutable());
    }

    @Test
    public void createInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CloseConnection.of(null, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Connection ID")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final CloseConnection expected =
                CloseConnection.of(TestConstants.ID, DittoHeaders.empty());

        final CloseConnection actual =
                CloseConnection.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                CloseConnection.of(TestConstants.ID, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

}
