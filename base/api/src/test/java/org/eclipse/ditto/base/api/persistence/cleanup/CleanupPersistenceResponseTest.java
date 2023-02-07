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
package org.eclipse.ditto.base.api.persistence.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link CleanupPersistenceResponse}.
 */
public final class CleanupPersistenceResponseTest {

    private static final EntityId ID = NamespacedEntityId.of(EntityType.of("thing"), "eclipse:ditto");
    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(CommandResponse.JsonFields.TYPE, CleanupPersistenceResponse.TYPE)
            .set(CleanupCommandResponse.JsonFields.ENTITY_TYPE, ID.getEntityType().toString())
            .set(CleanupCommandResponse.JsonFields.ENTITY_ID, ID.toString())
            .set(CommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .build();
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder().correlationId("123").build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(CleanupPersistenceResponse.class, areImmutable(), provided(EntityId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CleanupPersistenceResponse.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final var underTest = CleanupPersistenceResponse.success(ID, DittoHeaders.empty());

        assertThat(underTest.toJson()).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final var cleanupPersistenceResponse = CleanupPersistenceResponse.success(ID, HEADERS);

        final var deserialized = CleanupPersistenceResponse.fromJson(cleanupPersistenceResponse.toJson(),
                cleanupPersistenceResponse.getDittoHeaders());

        assertThat(deserialized).isEqualTo(cleanupPersistenceResponse);
    }

}
