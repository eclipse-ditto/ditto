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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.connectivity.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TestConnection}.
 */
public final class TestConnectionTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Command.JsonFields.TYPE, TestConnection.TYPE)
            .set(TestConnection.JSON_CONNECTION, TestConstants.CONNECTION.toJson())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TestConnection.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(TestConnection.class, areImmutable(), provided(Connection.class, MappingContext.class)
                .isAlsoImmutable
                ());
    }

    @Test
    public void createInstanceWithNullConnection() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> TestConnection.of(null, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Connection")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final TestConnection expected =
                TestConnection.of(TestConstants.CONNECTION, DittoHeaders.empty());

        final TestConnection actual =
                TestConnection.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                TestConnection.of(TestConstants.CONNECTION, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

}
