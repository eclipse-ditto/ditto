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
package org.eclipse.ditto.base.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ShutdownReasonFactory}.
 */
public final class ShutdownReasonFactoryTest {

    private static final String NAMESPACE = "com.example.test";
    private static final EntityType THING_TYPE = EntityType.of("thing");
    private static final List<EntityId> ENTITY_IDS_TO_PURGE = Arrays.asList(
            EntityId.of(THING_TYPE, "x:y"),
            EntityId.of(THING_TYPE, "a:b"),
            EntityId.of(THING_TYPE, "f:oo"));

    private static JsonObject purgeNamespaceReasonJson;
    private static JsonObject purgeEntitiesReasonJson;

    @BeforeClass
    public static void initTestConstants() {
        purgeNamespaceReasonJson = JsonFactory.newObjectBuilder()
                .set(ShutdownReason.JsonFields.TYPE, ShutdownReasonType.Known.PURGE_NAMESPACE.toString())
                .set(ShutdownReason.JsonFields.DETAILS, JsonValue.of(NAMESPACE))
                .build();

        purgeEntitiesReasonJson = JsonFactory.newObjectBuilder()
                .set(ShutdownReason.JsonFields.TYPE, ShutdownReasonType.Known.PURGE_ENTITIES.toString())
                .set(ShutdownReason.JsonFields.DETAILS, JsonArray.of(ENTITY_IDS_TO_PURGE))
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ShutdownReasonFactory.class, areImmutable());
    }

    @Test
    public void getPurgeNamespaceReasonFromJson() {
        assertThat(ShutdownReasonFactory.fromJson(purgeNamespaceReasonJson))
                .isEqualTo(ShutdownReasonFactory.getPurgeNamespaceReason(NAMESPACE));
    }

    @Test
    public void getPurgeEntitiesReasonFromJson() {
        assertThat(ShutdownReasonFactory.fromJson(purgeEntitiesReasonJson))
                .isEqualTo(ShutdownReasonFactory.getPurgeEntitiesReason(ENTITY_IDS_TO_PURGE));
    }

    @Test
    public void fromJsonWithoutType() {
        final JsonObject jsonObject = purgeNamespaceReasonJson.toBuilder()
                .remove(ShutdownReason.JsonFields.TYPE)
                .build();


        assertThat(ShutdownReasonFactory.fromJson(jsonObject)).isEqualTo(ShutdownNoReason.INSTANCE);
    }

    @Test
    public void fromJsonWithUnknownType() {
        final JsonObject shutdownReasonWithUnknownType = purgeEntitiesReasonJson.toBuilder()
                .set(ShutdownReason.JsonFields.TYPE, "fooBar")
                .build();

        assertThat(ShutdownReasonFactory.fromJson(shutdownReasonWithUnknownType)).isEqualTo(ShutdownNoReason.INSTANCE);
    }

}
