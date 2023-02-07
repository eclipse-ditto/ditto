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

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link CleanupPersistence} command.
 */
public class CleanupPersistenceTest {

    private static final EntityId ID = NamespacedEntityId.of(EntityType.of("thing"), "eclipse:ditto");
    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Command.JsonFields.TYPE, CleanupPersistence.TYPE)
            .set(CleanupCommand.JsonFields.ENTITY_TYPE, ID.getEntityType().toString())
            .set(CleanupCommand.JsonFields.ENTITY_ID, ID.toString())
            .build();
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder().correlationId("123").build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(CleanupPersistence.class, areImmutable(), provided(EntityId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CleanupPersistence.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject jsonObject = CleanupPersistence.of(ID, DittoHeaders.empty()).toJson();
        assertThat(jsonObject).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final CleanupPersistence commandFromJson = CleanupPersistence.fromJson(KNOWN_JSON, HEADERS);
        final CleanupPersistence expectedCommand = CleanupPersistence.of(ID, HEADERS);
        assertThat(commandFromJson).isEqualTo(expectedCommand);
    }
}
