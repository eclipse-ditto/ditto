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
package org.eclipse.ditto.signals.commands.common.purge;

import static org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.common.CommonCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PurgeEntitiesResponse}.
 */
public final class PurgeEntitiesResponseTest {

    private static final String ENTITY_TYPE = "policy";

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(CommonCommandResponse.JsonFields.TYPE, PurgeEntitiesResponse.TYPE)
            .set(CommonCommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .set(PurgeEntitiesResponse.JsonFields.ENTITY_TYPE, ENTITY_TYPE)
            .set(PurgeEntitiesResponse.JsonFields.SUCCESSFUL, true)
            .build();
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder()
            .correlationId(String.valueOf(UUID.randomUUID()))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(PurgeEntitiesResponse.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PurgeEntitiesResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final PurgeEntitiesResponse responseFromJson =
                PurgeEntitiesResponse.fromJson(KNOWN_JSON, HEADERS);

        assertThat(responseFromJson)
                .isEqualTo(PurgeEntitiesResponse.successful(ENTITY_TYPE, HEADERS));
    }

    @Test
    public void successfulResponseToJson() {
        final PurgeEntitiesResponse underTest =
                PurgeEntitiesResponse.successful(ENTITY_TYPE, HEADERS);

        DittoJsonAssertions.assertThat(underTest.toJson()).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void failedResponseToJson() {
        final JsonObject expectedJson = KNOWN_JSON.toBuilder()
                .set(PurgeEntitiesResponse.JsonFields.SUCCESSFUL, false)
                .build();

        final PurgeEntitiesResponse underTest = PurgeEntitiesResponse.failed(ENTITY_TYPE, HEADERS);

        DittoJsonAssertions.assertThat(underTest.toJson()).isEqualTo(expectedJson);
    }

}