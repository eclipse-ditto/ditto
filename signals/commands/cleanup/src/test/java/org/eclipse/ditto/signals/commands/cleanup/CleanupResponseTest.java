/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link CleanupResponse}.
 */
public class CleanupResponseTest {

    private static final String ID = "thing:eclipse:ditto";
    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(CommandResponse.JsonFields.TYPE, CleanupResponse.TYPE)
            .set(CleanupCommandResponse.JsonFields.ENTITY_ID, ID)
            .set(CommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .build();
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder().correlationId("123").build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(CleanupResponse.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CleanupResponse.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject jsonObject = CleanupResponse.success(ID, DittoHeaders.empty()).toJson();
        assertThat(jsonObject).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final CleanupResponse commandFromJson = CleanupResponse.fromJson(KNOWN_JSON, HEADERS);
        final CleanupResponse expectedCommand = CleanupResponse.success(ID, HEADERS);
        assertThat(commandFromJson).isEqualTo(expectedCommand);
    }

}