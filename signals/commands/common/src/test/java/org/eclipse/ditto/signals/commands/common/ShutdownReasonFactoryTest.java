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
package org.eclipse.ditto.signals.commands.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.signals.commands.common.ShutdownReasonFactory}.
 */
public final class ShutdownReasonFactoryTest {

    private static final String NAMESPACE = "com.example.test";
    private static final List<String> ENTITY_IDS_TO_PURGE = Arrays.asList("x:y", "a:b", "f:oo");

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
    public void fromJsonWithoutTypeFails() {
        final JsonObject jsonObject = purgeNamespaceReasonJson.toBuilder()
                .remove(ShutdownReason.JsonFields.TYPE)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> ShutdownReasonFactory.fromJson(jsonObject))
                .withMessageContaining(ShutdownReason.JsonFields.TYPE.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void fromJsonWithUnknownTypeFails() {
        final JsonObject shutdownReasonWithUnknownType = purgeEntitiesReasonJson.toBuilder()
                .set(ShutdownReason.JsonFields.TYPE, "fooBar")
                .build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ShutdownReasonFactory.fromJson(shutdownReasonWithUnknownType))
                .withMessage("Unknown shutdown reason type: <fooBar>.");
    }

}
