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
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveResolvedHonoConnection}.
 */
public final class RetrieveResolvedHonoConnectionTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Command.JsonFields.TYPE, RetrieveResolvedHonoConnection.TYPE)
            .set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, TestConstants.ID.toString())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveResolvedHonoConnection.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveResolvedHonoConnection.class,
                areImmutable(),
                provided(ConnectionId.class, JsonFieldSelector.class).isAlsoImmutable());
    }

    @Test
    public void createInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveResolvedHonoConnection.of(null, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "connectionId")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveResolvedHonoConnection expected =
                RetrieveResolvedHonoConnection.of(TestConstants.ID, DittoHeaders.empty());

        final RetrieveResolvedHonoConnection actual =
                RetrieveResolvedHonoConnection.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                RetrieveResolvedHonoConnection.of(TestConstants.ID, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void getEntityIdReturnsExpected() {
        final RetrieveResolvedHonoConnection actual =
                RetrieveResolvedHonoConnection.of(TestConstants.ID, DittoHeaders.empty());

        assertThat((CharSequence) actual.getEntityId()).isEqualTo(ConnectionId.of(TestConstants.ID));
    }

    @Test
    public void setDittoHeadersReturnsExpected() {
        Map<String, String> map = new HashMap<>();
        map.put("header1_key", "header1_value");
        map.put("header2_key", "header2_value");
        map.put("header3_key", "header3_value");
        final DittoHeaders EXPECTED_DITTO_HEADERS = DittoHeaders.of(map);
        final RetrieveResolvedHonoConnection actual =
                RetrieveResolvedHonoConnection.of(TestConstants.ID, DittoHeaders.empty());

        assertThat(actual.getDittoHeaders()).isEmpty();
        RetrieveResolvedHonoConnection changed = actual.setDittoHeaders(EXPECTED_DITTO_HEADERS);
        assertThat(changed.getDittoHeaders()).isEqualTo(EXPECTED_DITTO_HEADERS);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = JsonFactory.emptyPointer();

        final RetrieveResolvedHonoConnection underTest =
                RetrieveResolvedHonoConnection.of(TestConstants.ID, DittoHeaders.empty());

        DittoJsonAssertions.assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
