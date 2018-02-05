/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.things;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableFeatureDefinitionIdentifier}.
 */
public final class ImmutableFeatureDefinitionIdentifierTest {

    private static final String NAMESPACE = "org.eclipse.ditto";
    private static final String NAME = "vorto";
    private static final String VERSION = "0.1.0";
    private static final String VALID_IDENTIFIER_STRING = NAMESPACE + ":" + NAME + ":" + VERSION;

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableFeatureDefinitionIdentifier.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableFeatureDefinitionIdentifier.class)
                .usingGetClass()
                .withIgnoredFields("stringRepresentation") // as it is only derived from other properties
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullNamespace() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.getInstance(null, NAME, VERSION))
                .withMessage("The %s must not be null!", "namespace")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithEmptyNamespace() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.getInstance("", NAME, VERSION))
                .withMessage("The argument '%s' must not be empty!", "namespace")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullName() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.getInstance(NAMESPACE, null, VERSION))
                .withMessage("The %s must not be null!", "name")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithEmptyName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.getInstance(NAMESPACE, "", VERSION))
                .withMessage("The argument '%s' must not be empty!", "name")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullVersion() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.getInstance(NAMESPACE, NAME, null))
                .withMessage("The %s must not be null!", "version")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithEmptyVersion() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.getInstance(NAMESPACE, NAME, ""))
                .withMessage("The argument '%s' must not be empty!", "version")
                .withNoCause();
    }

    @Test
    public void getNamespaceReturnsExpected() {
        final ImmutableFeatureDefinitionIdentifier underTest =
                ImmutableFeatureDefinitionIdentifier.getInstance(NAMESPACE, NAME, VERSION);

        assertThat(underTest.getNamespace()).isEqualTo(NAMESPACE);
    }

    @Test
    public void getNameReturnsExpected() {
        final ImmutableFeatureDefinitionIdentifier underTest =
                ImmutableFeatureDefinitionIdentifier.getInstance(NAMESPACE, NAME, VERSION);

        assertThat(underTest.getName()).isEqualTo(NAME);
    }

    @Test
    public void getVersionReturnsExpected() {
        final ImmutableFeatureDefinitionIdentifier underTest =
                ImmutableFeatureDefinitionIdentifier.getInstance(NAMESPACE, NAME, VERSION);

        assertThat(underTest.getVersion()).isEqualTo(VERSION);
    }

    @Test
    public void toStringReturnsExpected() {
        final ImmutableFeatureDefinitionIdentifier underTest =
                ImmutableFeatureDefinitionIdentifier.getInstance(NAMESPACE, NAME, VERSION);

        assertThat(underTest.toString()).isEqualTo(VALID_IDENTIFIER_STRING);
    }

    @Test
    public void tryToParseNullIdentifierString() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.ofParsed(null))
                .withMessage("The %s must not be null!", "CharSequence-representation of the identifier")
                .withNoCause();
    }

    @Test
    public void tryToParseEmptyIdentifierString() {
        assertThatExceptionOfType(FeatureDefinitionIdentifierInvalidException.class)
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.ofParsed(""))
                .withMessage("Feature Definition Identifier <> is invalid!")
                .withNoCause();
    }

    @Test
    public void tryToParseIdentifierStringWithEmptyNameSegment() {
        final String invalidString = NAMESPACE + "::" + VERSION;

        assertThatExceptionOfType(FeatureDefinitionIdentifierInvalidException.class)
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.ofParsed(invalidString))
                .withMessage("Feature Definition Identifier <%s> is invalid!", invalidString)
                .withNoCause();
    }

    @Test
    public void tryToParseIdentifierStringWithIllegalChar() {
        final String invalidString = "org/eclipse/ditto" + ":" + NAME + ":" + VERSION;

        assertThatExceptionOfType(FeatureDefinitionIdentifierInvalidException.class)
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.ofParsed(invalidString))
                .withMessage("Feature Definition Identifier <%s> is invalid!", invalidString)
                .withNoCause();
    }

    @Test
    public void parseValidIdentifierString() {
        final ImmutableFeatureDefinitionIdentifier actual =
                ImmutableFeatureDefinitionIdentifier.ofParsed(VALID_IDENTIFIER_STRING);

        final ImmutableFeatureDefinitionIdentifier expected =
                ImmutableFeatureDefinitionIdentifier.getInstance(NAMESPACE, NAME, VERSION);

        assertThat(actual).isEqualTo(expected);
    }

}
