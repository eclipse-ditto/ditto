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
package org.eclipse.ditto.signals.commands.amqpbridge.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.amqpbridge.TestConstants;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;

/**
 * Unit test for {@link RetrieveConnectionStatusesResponse}.
 */
public final class RetrieveConnectionStatusesResponseTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(CommandResponse.JsonFields.TYPE, RetrieveConnectionStatusesResponse.TYPE)
            .set(CommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .set(RetrieveConnectionStatusesResponse.JSON_CONNECTION_STATUSES, JsonObject.newBuilder()
                    .set(TestConstants.ID, ConnectionStatus.OPEN.getName())
                    .build())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveConnectionStatusesResponse.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveConnectionStatusesResponse.class, areImmutable(),
                provided(ConnectionStatus.class).isAlsoImmutable());
    }

    @Test
    public void retrieveInstanceWithNullConnections() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveConnectionStatusesResponse.of(null, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Connection Statuses")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveConnectionStatusesResponse expected =
                RetrieveConnectionStatusesResponse.of(TestConstants.CONNECTION_STATUSES, DittoHeaders.empty());

        final RetrieveConnectionStatusesResponse actual =
                RetrieveConnectionStatusesResponse.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                RetrieveConnectionStatusesResponse.of(TestConstants.CONNECTION_STATUSES, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

}
