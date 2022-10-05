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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableFeatureDefinitionIdentifier}.
 */
public final class ImmutableFeatureDefinitionIdentifierTest {

    private static final String NAMESPACE = "org.eclipse.ditto";
    private static final String NAME = "example";
    private static final String VERSION = "0.1.0";
    private static final String VALID_IDENTIFIER_STRING = NAMESPACE + ":" + NAME + ":" + VERSION;

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableFeatureDefinitionIdentifier.class, areImmutable(),
                provided(DefinitionIdentifier.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableFeatureDefinitionIdentifier.class)
                .usingGetClass()
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

        assertThat(underTest.toString()).hasToString(VALID_IDENTIFIER_STRING);
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
        assertThatExceptionOfType(DefinitionIdentifierInvalidException.class)
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.ofParsed(""))
                .withMessage("Definition identifier <> is invalid!")
                .withNoCause();
    }

    @Test
    public void tryToParseIdentifierStringWithEmptyNameSegment() {
        final String invalidString = NAMESPACE + "::" + VERSION;

        assertThatExceptionOfType(DefinitionIdentifierInvalidException.class)
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.ofParsed(invalidString))
                .withMessage("Definition identifier <%s> is invalid!", invalidString)
                .withNoCause();
    }

    @Test
    public void tryToParseIdentifierStringWithIllegalChar() {
        final String invalidString = "org/eclipse/ditto" + ":" + NAME + ":" + VERSION;

        assertThatExceptionOfType(DefinitionIdentifierInvalidException.class)
                .isThrownBy(() -> ImmutableFeatureDefinitionIdentifier.ofParsed(invalidString))
                .withMessage("Definition identifier <%s> is invalid!", invalidString)
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
