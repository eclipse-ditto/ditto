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
package org.eclipse.ditto.connectivity.model.signals.commands.modify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.signals.commands.TestConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link CreateConnection}.
 */
public final class CreateConnectionTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Command.JsonFields.TYPE, CreateConnection.TYPE)
            .set(CreateConnection.JSON_CONNECTION, TestConstants.CONNECTION.toJson())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CreateConnection.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(CreateConnection.class, areImmutable(), provided(Connection.class, MappingContext.class)
                .isAlsoImmutable
                ());
    }

    @Test
    public void createInstanceWithNullConnection() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CreateConnection.of(null, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Connection")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final CreateConnection expected =
                CreateConnection.of(TestConstants.CONNECTION, DittoHeaders.empty());

        final CreateConnection actual =
                CreateConnection.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                CreateConnection.of(TestConstants.CONNECTION, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = JsonFactory.emptyPointer();

        final CreateConnection underTest =
                CreateConnection.of(TestConstants.CONNECTION, DittoHeaders.empty());

        DittoJsonAssertions.assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
