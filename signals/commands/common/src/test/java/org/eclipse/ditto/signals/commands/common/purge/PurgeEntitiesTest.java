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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.common.CommonCommandResponse;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PurgeEntities}.
 */
public final class PurgeEntitiesTest {

    private static final List<EntityId> ENTITY_IDS =
            Collections.unmodifiableList(
                    Arrays.asList(DefaultEntityId.of("my.ns1:id"), DefaultEntityId.of("my.ns2:id")));
    private static final String ENTITY_TYPE = "policy";

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(CommonCommandResponse.JsonFields.TYPE, PurgeEntities.TYPE)
            .set(PurgeEntities.JsonFields.ENTITY_TYPE, ENTITY_TYPE)
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
