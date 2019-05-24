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
package org.eclipse.ditto.signals.commands.connectivity.modify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.signals.commands.connectivity.modify.CheckConnectionLogsActive}.
 */
public final class CheckConnectionLogsActiveTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Command.JsonFields.TYPE, CheckConnectionLogsActive.TYPE)
            .set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, TestConstants.ID)
            .set(CheckConnectionLogsActive.JSON_TIMESTAMP, TestConstants.TIMESTAMP)
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CheckConnectionLogsActive.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(CheckConnectionLogsActive.class, areImmutable());
    }

    @Test
    public void createInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CheckConnectionLogsActive.of(null, Instant.now(), DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Connection ID")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final CheckConnectionLogsActive expected =
                CheckConnectionLogsActive.of(TestConstants.ID, Instant.parse(TestConstants.TIMESTAMP));

        final CheckConnectionLogsActive actual =
                CheckConnectionLogsActive.fromJson(KNOWN_JSON);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                CheckConnectionLogsActive.of(TestConstants.ID, Instant.parse(TestConstants.TIMESTAMP)).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

}