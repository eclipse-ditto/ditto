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
package org.eclipse.ditto.base.api.common.purge;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.base.api.common.CommonCommandResponse;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.assertions.CommandAssertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PurgeEntitiesResponse}.
 */
public final class PurgeEntitiesResponseTest {

    private static final EntityType ENTITY_TYPE = EntityType.of("policy");

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(CommonCommandResponse.JsonFields.TYPE, PurgeEntitiesResponse.TYPE)
            .set(CommonCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(PurgeEntitiesResponse.JsonFields.ENTITY_TYPE, ENTITY_TYPE.toString())
            .set(PurgeEntitiesResponse.JsonFields.SUCCESSFUL, true)
            .build();
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder()
            .correlationId(String.valueOf(UUID.randomUUID()))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(PurgeEntitiesResponse.class,
                areImmutable(),
                provided(EntityType.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PurgeEntitiesResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final var responseFromJson = PurgeEntitiesResponse.fromJson(KNOWN_JSON, HEADERS);

        CommandAssertions.assertThat(responseFromJson)
                .isEqualTo(PurgeEntitiesResponse.successful(ENTITY_TYPE, HEADERS));
    }

    @Test
    public void successfulResponseToJson() {
        final var underTest = PurgeEntitiesResponse.successful(ENTITY_TYPE, HEADERS);

        DittoJsonAssertions.assertThat(underTest.toJson()).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void failedResponseToJson() {
        final var expectedJson = JsonFactory.newObjectBuilder(KNOWN_JSON)
                .set(CommandResponse.JsonFields.STATUS, HttpStatus.INTERNAL_SERVER_ERROR.getCode())
                .set(PurgeEntitiesResponse.JsonFields.SUCCESSFUL, false)
                .build();

        final var underTest = PurgeEntitiesResponse.failed(ENTITY_TYPE, HEADERS);

        DittoJsonAssertions.assertThat(underTest.toJson()).isEqualTo(expectedJson);
    }

}
