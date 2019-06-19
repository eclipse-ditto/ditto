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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PurgeNamespaceReason}.
 */
public final class PurgeEntitiesReasonTest {

    private static ShutdownReasonType purgeEntitiesType;
    private static List<String> knownEntityIds;
    private static JsonObject knownJsonRepresentation;

    private PurgeEntitiesReason underTest;

    @BeforeClass
    public static void initTestConstants() {
        purgeEntitiesType = ShutdownReasonType.Known.PURGE_ENTITIES;
        knownEntityIds = Arrays.asList("x:y", "a:b", "f:oo");
        knownJsonRepresentation = JsonFactory.newObjectBuilder()
                .set(ShutdownReason.JsonFields.TYPE, purgeEntitiesType.toString())
                .set(ShutdownReason.JsonFields.DETAILS, JsonArray.of(knownEntityIds))
                .build();
    }

    @Before
    public void setUp() {
        underTest = PurgeEntitiesReason.of(knownEntityIds);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(PurgeNamespaceReason.class, areImmutable(), provided(ShutdownReason.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PurgeNamespaceReason.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getTypeReturnsPurgeEntities() {
        assertThat(underTest.getType()).isEqualTo(purgeEntitiesType);
    }

    @Test
    public void tryToGetInstanceWithEmptyNamespace() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PurgeNamespaceReason.of(""))
                .withMessageContaining("namespace")
                .withMessageContaining("not be empty")
                .withNoCause();
    }

    @Test
    public void toJsonWithoutSchemaVersionAndPredicateReturnsExpected() {
        assertThat(underTest.toJson()).isEqualTo(knownJsonRepresentation);
    }

    @Test
    public void toJsonWithHiddenFieldsOnlyReturnsEmptyJsonObject() {
        assertThat(underTest.toJson(FieldType.HIDDEN)).isEmpty();
    }

    @Test
    public void fromJson() {
        assertThat(PurgeEntitiesReason.fromJson(knownJsonRepresentation)).isEqualTo(underTest);
    }

    @Test
    public void fromJsonWithoutDetailsCausesException() {
        final JsonObject shutDownNamespaceReasonWithoutDetails = knownJsonRepresentation.toBuilder()
                .remove(ShutdownReason.JsonFields.DETAILS)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class).isThrownBy(
                () -> PurgeNamespaceReason.fromJson(shutDownNamespaceReasonWithoutDetails));
    }

    @Test
    public void isRelevantForIsTrueIfNamespaceIsEqual() {
        assertThat(underTest.isRelevantFor("f:oo")).isTrue();
    }

    @Test
    public void isRelevantForIsFalseIfNamespaceIsNotEqual() {
        assertThat(underTest.isRelevantFor("b:ar")).isFalse();
    }

    @Test
    public void toStringContainsExpected() {
        assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains(knownEntityIds.toString());
    }

}
