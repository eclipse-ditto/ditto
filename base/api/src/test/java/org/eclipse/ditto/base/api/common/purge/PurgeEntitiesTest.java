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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.eclipse.ditto.base.api.common.CommonCommandResponse;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PurgeEntities}.
 */
public final class PurgeEntitiesTest {

    private static final EntityType ENTITY_TYPE = EntityType.of("policy");
    private static final List<EntityId> ENTITY_IDS =
            Lists.list(EntityId.of(ENTITY_TYPE, "my.ns1:id"), EntityId.of(ENTITY_TYPE, "my.ns2:id"));

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(CommonCommandResponse.JsonFields.TYPE, PurgeEntities.TYPE)
            .set(PurgeEntities.JsonFields.ENTITY_TYPE, ENTITY_TYPE.toString())
            .set(PurgeEntities.JsonFields.ENTITY_IDS, JsonArray.of(ENTITY_IDS))
            .build();
    private static final DittoHeaders HEADERS = DittoHeaders.newBuilder()
            .correlationId(String.valueOf(UUID.randomUUID()))
            .build();

    private PurgeEntities underTest;

    @Before
    public void setUp() {
        underTest = PurgeEntities.of(ENTITY_TYPE, ENTITY_IDS, HEADERS);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(PurgeEntities.class, areImmutable(),
                provided(EntityType.class).isAlsoImmutable(),
                assumingFields("entityIds").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PurgeEntities.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final PurgeEntities commandFromJson = PurgeEntities.fromJson(KNOWN_JSON, HEADERS);

        assertThat(commandFromJson).isEqualTo(underTest);
    }

    @Test
    public void toJsonReturnsExpected() {
        Assertions.assertThat(underTest.toJson()).isEqualTo(KNOWN_JSON);
    }

}
