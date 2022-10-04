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

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.signals.commands.TestConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveHonoConnectionResponse}.
 */
public final class RetrieveHonoConnectionResponseTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(CommandResponse.JsonFields.TYPE, RetrieveHonoConnectionResponse.TYPE)
            .set(CommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(RetrieveHonoConnectionResponse.JSON_CONNECTION, TestConstants.HONO_CONNECTION.toJson())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveHonoConnectionResponse.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveHonoConnectionResponse.class,
                areImmutable(),
                provided(JsonObject.class, MappingContext.class).isAlsoImmutable());
    }

    @Test
    public void retrieveInstanceWithNullConnection() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveHonoConnectionResponse.of(null, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "connection")
                .withNoCause();
    }

    @Test
    public void retrieveInstanceWithNonHonoConnection() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> RetrieveHonoConnectionResponse.of(TestConstants.CONNECTION.toJson(), DittoHeaders.empty()))
                .withMessage("The %s must be of type 'Hono'!", "connection")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveHonoConnectionResponse expected =
                RetrieveHonoConnectionResponse.of(TestConstants.HONO_CONNECTION.toJson(), DittoHeaders.empty());

        final RetrieveHonoConnectionResponse actual =
                RetrieveHonoConnectionResponse.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrieveHonoConnectionResponse underTest =
                RetrieveHonoConnectionResponse.of(TestConstants.HONO_CONNECTION.toJson(), DittoHeaders.empty());

        assertThat(underTest.toJson()).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = JsonFactory.emptyPointer();

        final RetrieveHonoConnectionResponse underTest =
                RetrieveHonoConnectionResponse.of(TestConstants.HONO_CONNECTION.toJson(), DittoHeaders.empty());

        DittoJsonAssertions.assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

}
