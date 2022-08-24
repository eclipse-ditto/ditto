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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PurgeNamespaceReason}.
 */
public final class PurgeNamespaceReasonTest {

    private static ShutdownReasonType purgeNamespaceType;
    private static String knownNamespace;
    private static JsonObject knownJsonRepresentation;

    private PurgeNamespaceReason underTest;

    @BeforeClass
    public static void initTestConstants() {
        purgeNamespaceType = ShutdownReasonType.Known.PURGE_NAMESPACE;
        knownNamespace = "com.example.test";
        knownJsonRepresentation = JsonFactory.newObjectBuilder()
                .set(ShutdownReason.JsonFields.TYPE, purgeNamespaceType.toString())
                .set(ShutdownReason.JsonFields.DETAILS, JsonValue.of(knownNamespace))
                .build();
    }

    @Before
    public void setUp() {
        underTest = PurgeNamespaceReason.of(knownNamespace);
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
    public void getTypeReturnsPurgeNamespace() {
        assertThat(underTest.getType()).isEqualTo(purgeNamespaceType);
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
        assertThat(PurgeNamespaceReason.fromJson(knownJsonRepresentation)).isEqualTo(underTest);
    }

    @Test
    public void fromJsonWithoutDetailsCausesException() {
        final JsonObject shutDownNamespaceReasonWithoutDetails = knownJsonRepresentation.toBuilder()
                .remove(ShutdownReason.JsonFields.DETAILS)
                .build();

        assertThatExceptionOfType(JsonMissingFieldException.class).isThrownBy(
                () -> PurgeNamespaceReason.fromJson(shutDownNamespaceReasonWithoutDetails))
                .withMessageContaining(ShutdownReason.JsonFields.DETAILS.getPointer().toString())
                .withNoCause();
    }

    @Test
    public void isRelevantForIsTrueIfNamespaceIsEqual() {
        assertThat(underTest.isRelevantFor(knownNamespace)).isTrue();
    }

    @Test
    public void isRelevantForIsFalseIfNamespaceIsNotEqual() {
        assertThat(underTest.isRelevantFor(knownNamespace + "X")).isFalse();
    }

    @Test
    public void toStringContainsExpected() {
        assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains(knownNamespace);
    }

}
