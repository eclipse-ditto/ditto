/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.NoSuchElementException;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.signals.commands.common.GenericReason}.
 */
public final class GenericReasonTest {

    private static ShutdownReasonType knownType;
    private static String knownDetails;
    private static JsonObject knownJsonRepresentation;

    @BeforeClass
    public static void initTestConstants() {
        knownType = ShutdownReasonType.Unknown.of("brace-yourselves");
        knownDetails = "Winter is coming!";
        knownJsonRepresentation = JsonFactory.newObjectBuilder()
                .set(ShutdownReason.JsonFields.TYPE, knownType.toString())
                .set(ShutdownReason.JsonFields.DETAILS, knownDetails)
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(GenericReason.class, areImmutable(), provided(ShutdownReasonType.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(GenericReason.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test
    public void getTypeReturnsExpected() {
        final GenericReason underTest = GenericReason.getInstance(knownType, null);

        assertThat(underTest.getType()).isEqualTo(knownType);
    }

    @Test
    public void reasonWithoutDetailsReturnsEmptyOptional() {
        final GenericReason underTest = GenericReason.getInstance(knownType, null);

        assertThat(underTest.getDetails()).isEmpty();
    }

    @Test
    public void getDetailsReturnsExpected() {
        final GenericReason underTest = GenericReason.getInstance(knownType, knownDetails);

        assertThat(underTest.getDetails()).contains(knownDetails);
    }

    @Test
    public void getDetailsAndThrowThrowsExceptionReasonHasNoDetails() {
        final GenericReason underTest = GenericReason.getInstance(knownType, null);

        assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(underTest::getDetailsOrThrow)
                .withMessage("This reason does not provide details!")
                .withNoCause();
    }

    @Test
    public void toJsonWithoutSchemaVersionAndPredicateReturnsExpected() {
        final GenericReason underTest = GenericReason.getInstance(knownType, knownDetails);

        assertThat(underTest.toJson()).isEqualTo(knownJsonRepresentation);
    }

    @Test
    public void toJsonWithHiddenFieldsOnlyReturnsEmptyJsonObject() {
        final GenericReason underTest = GenericReason.getInstance(knownType, knownDetails);

        assertThat(underTest.toJson(FieldType.HIDDEN)).isEmpty();
    }

    @Test
    public void toStringContainsExpected() {
        final GenericReason underTest = GenericReason.getInstance(knownType, knownDetails);

        assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains(knownType)
                .contains(knownDetails);
    }

}