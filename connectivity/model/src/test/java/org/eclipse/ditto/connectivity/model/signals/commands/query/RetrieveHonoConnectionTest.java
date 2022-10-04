/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model.signals.commands.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.TestConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveHonoConnection}.
 */
public final class RetrieveHonoConnectionTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Command.JsonFields.TYPE, RetrieveHonoConnection.TYPE)
            .set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, TestConstants.ID.toString())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveHonoConnection.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveHonoConnection.class,
                areImmutable(),
                provided(ConnectionId.class).isAlsoImmutable());
    }

    @Test
    public void createInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveHonoConnection.of(null, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "connectionId")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveHonoConnection expected =
                RetrieveHonoConnection.of(TestConstants.ID, DittoHeaders.empty());

        final RetrieveHonoConnection actual =
                RetrieveHonoConnection.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                RetrieveHonoConnection.of(TestConstants.ID, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void getEntityIdReturnsExpected() {
        final RetrieveHonoConnection actual =
                RetrieveHonoConnection.of(TestConstants.ID, DittoHeaders.empty());

        assertThat((CharSequence) actual.getEntityId()).isEqualTo(ConnectionId.of(TestConstants.ID));
    }

    @Test
    public void setDittoHeadersReturnsExpected() {
        Map<String, String> map = new HashMap<>();
        map.put("header1_key", "header1_value");
        map.put("header2_key", "header2_value");
        map.put("header3_key", "header3_value");
        final DittoHeaders EXPECTED_DITTO_HEADERS = DittoHeaders.of(map);
        final RetrieveHonoConnection actual =
                RetrieveHonoConnection.of(TestConstants.ID, DittoHeaders.empty());

        assertThat(actual.getDittoHeaders()).isEmpty();
        RetrieveHonoConnection changed = actual.setDittoHeaders(EXPECTED_DITTO_HEADERS);
        assertThat(changed.getDittoHeaders()).isEqualTo(EXPECTED_DITTO_HEADERS);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = JsonFactory.emptyPointer();

        final RetrieveHonoConnection underTest =
                RetrieveHonoConnection.of(TestConstants.ID, DittoHeaders.empty());

        DittoJsonAssertions.assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
