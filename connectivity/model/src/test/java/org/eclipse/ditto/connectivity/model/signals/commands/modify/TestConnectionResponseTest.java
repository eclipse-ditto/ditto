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

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.TestConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TestConnectionResponse}.
 */
public final class TestConnectionResponseTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(CommandResponse.JsonFields.TYPE, TestConnectionResponse.TYPE)
            .set(CommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, TestConstants.CONNECTION.getId().toString())
            .set(TestConnectionResponse.JSON_TEST_RESULT, "connected")
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TestConnectionResponse.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(TestConnectionResponse.class,
                areImmutable(),
                provided(Connection.class, MappingContext.class, ConnectionId.class).isAlsoImmutable());
    }

    @Test
    public void createInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> TestConnectionResponse.success(null, "connected", DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "connectionId")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final TestConnectionResponse expected =
                TestConnectionResponse.success(TestConstants.CONNECTION.getId(), "connected", DittoHeaders.empty());

        final TestConnectionResponse actual =
                TestConnectionResponse.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final TestConnectionResponse underTest =
                TestConnectionResponse.success(TestConstants.CONNECTION.getId(), "connected", DittoHeaders.empty());

        assertThat(underTest.toJson()).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = JsonFactory.emptyPointer();

        final TestConnectionResponse underTest =
                TestConnectionResponse.success(TestConstants.ID, "connected", DittoHeaders.empty());

        DittoJsonAssertions.assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }
}
