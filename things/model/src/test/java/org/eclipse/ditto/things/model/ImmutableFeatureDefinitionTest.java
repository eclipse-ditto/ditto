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
package org.eclipse.ditto.things.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableFeatureDefinition} and its builder.
 */
public final class ImmutableFeatureDefinitionTest {

    private static final DefinitionIdentifier FIRST_IDENTIFIER =
            ThingsModelFactory.newFeatureDefinitionIdentifier("org.eclipse.ditto:example:0.1.0");

    private static final DefinitionIdentifier SECOND_IDENTIFIER =
            ThingsModelFactory.newFeatureDefinitionIdentifier("org.eclipse.ditto:example:1.0.0");

    private static final DefinitionIdentifier THIRD_IDENTIFIER =
            ThingsModelFactory.newFeatureDefinitionIdentifier("foo:bar:2.0.0");

    private static final JsonArray VALID_JSON = JsonFactory.newArrayBuilder()
            .add(FIRST_IDENTIFIER.toString(), SECOND_IDENTIFIER.toString(),THIRD_IDENTIFIER.toString())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableFeatureDefinition.class,
                areImmutable(),
                provided(DefinitionIdentifier.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableFeatureDefinition.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToGetBuilderWithNullIdentifier() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableFeatureDefinition.getBuilder(null))
                .withMessage("The %s must not be null!", "first identifier")
                .withNoCause();
    }

    @Test
    public void getFirstIdentifierFromBuilderReturnsExpected() {
        final FeatureDefinitionBuilder underTest = ImmutableFeatureDefinition.getBuilder(FIRST_IDENTIFIER);

        assertThat(underTest.getFirstIdentifier()).isEqualTo(FIRST_IDENTIFIER);
    }

    @Test
    public void getSizeFromBuilderReturnsExpected() {
        final FeatureDefinitionBuilder underTest = ImmutableFeatureDefinition.getBuilder(FIRST_IDENTIFIER);

        assertThat(underTest.getSize()).isOne();
    }

    @Test
    public void addIdentifierToBuilderWorksAsExpected() {
        final FeatureDefinitionBuilder underTest = ImmutableFeatureDefinition.getBuilder(FIRST_IDENTIFIER);
        underTest.add(SECOND_IDENTIFIER);

        assertThat(underTest).containsExactly(FIRST_IDENTIFIER, SECOND_IDENTIFIER);
    }

    @Test
    public void addAllToBuilderWorksAsExpected() {
        final List<DefinitionIdentifier> additionalIdentifiers = Arrays.asList(THIRD_IDENTIFIER, SECOND_IDENTIFIER);

        final FeatureDefinitionBuilder underTest = ImmutableFeatureDefinition.getBuilder(FIRST_IDENTIFIER);
        underTest.addAll(additionalIdentifiers);

        assertThat(underTest).containsExactly(FIRST_IDENTIFIER, THIRD_IDENTIFIER, SECOND_IDENTIFIER);
    }

    @Test
    public void removeIdentifierFromBuilderWorksAsExpected() {
        final FeatureDefinitionBuilder underTest = ImmutableFeatureDefinition.getBuilder(FIRST_IDENTIFIER);
        underTest.add(SECOND_IDENTIFIER);
        underTest.remove(FIRST_IDENTIFIER);

        assertThat(underTest).containsOnly(SECOND_IDENTIFIER);
    }

    @Test
    public void tryToBuildFeatureDefinitionWithEmptyBuilder() {
        final FeatureDefinitionBuilder underTest = ImmutableFeatureDefinition.getBuilder(FIRST_IDENTIFIER);
        underTest.remove(FIRST_IDENTIFIER);

        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(underTest::build)
                .withMessage("This builder does not contain at least one Identifier!")
                .withNoCause();
    }

    @Test
    public void getFirstIdentifierReturnsExpected() {
        final ImmutableFeatureDefinition underTest = ImmutableFeatureDefinition.getBuilder(FIRST_IDENTIFIER)
                .add(SECOND_IDENTIFIER)
                .add(THIRD_IDENTIFIER)
                .build();

        assertThat(underTest.getFirstIdentifier()).isEqualTo(FIRST_IDENTIFIER);
    }

    @Test
    public void getSizeReturnsExpected() {
        final ImmutableFeatureDefinition underTest = ImmutableFeatureDefinition.getBuilder(FIRST_IDENTIFIER)
                .add(SECOND_IDENTIFIER)
                .add(THIRD_IDENTIFIER)
                .build();

        assertThat(underTest.getSize()).isEqualTo(3);
    }

    @Test
    public void valuesAreInExpectedOrder() {
        final ImmutableFeatureDefinition underTest = ImmutableFeatureDefinition.getBuilder(SECOND_IDENTIFIER)
                .add(THIRD_IDENTIFIER)
                .add(FIRST_IDENTIFIER)
                .build();

        assertThat(underTest).containsExactly(SECOND_IDENTIFIER, THIRD_IDENTIFIER, FIRST_IDENTIFIER);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ImmutableFeatureDefinition underTest = ImmutableFeatureDefinition.getBuilder(FIRST_IDENTIFIER)
                .add(SECOND_IDENTIFIER)
                .add(THIRD_IDENTIFIER)
                .build();

        final JsonArray actual = underTest.toJson();

        assertThat(actual).isEqualTo(VALID_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final ImmutableFeatureDefinition actual = ImmutableFeatureDefinition.fromJson(VALID_JSON);

        final ImmutableFeatureDefinition expected = ImmutableFeatureDefinition.getBuilder(FIRST_IDENTIFIER)
                .add(SECOND_IDENTIFIER)
                .add(THIRD_IDENTIFIER)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void fromJsonOfEmptyArrayFailsWithException() {
        assertThatExceptionOfType(FeatureDefinitionEmptyException.class)
                .isThrownBy(() -> ImmutableFeatureDefinition.fromJson(JsonFactory.newArray()))
                .withMessage("Feature Definition must not be empty!")
                .withNoCause();
    }

    @Test
    public void fromJsonWithIncorrectTypeFailsWithException() {
        final JsonArray jsonArray = JsonFactory.newArrayBuilder()
                .add(1.5)
                .add(FIRST_IDENTIFIER.toString())
                .build();

        assertThatExceptionOfType(DefinitionIdentifierInvalidException.class)
                .isThrownBy(() -> ImmutableFeatureDefinition.fromJson(jsonArray))
                .withMessage("Definition identifier <1.5> is invalid!")
                .withNoCause();
    }

}
