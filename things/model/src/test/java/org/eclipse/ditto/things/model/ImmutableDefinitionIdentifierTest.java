/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableDefinitionIdentifier}.
 */
public final class ImmutableDefinitionIdentifierTest {

    private static final String NAMESPACE = "org.eclipse.ditto";
    private static final String NAME = "example";
    private static final String VERSION = "0.1.0";
    private static final String VALID_IDENTIFIER_STRING = NAMESPACE + ":" + NAME + ":" + VERSION;

    private static final String VALID_IDENTIFIER_URL_STR = "https://ditto.eclipseprojects.io/some/file.json";
    private static URL VALID_IDENTIFIER_URL;


    static {
        try {
            VALID_IDENTIFIER_URL = new URL(VALID_IDENTIFIER_URL_STR);
        } catch (final MalformedURLException e) {
            // ignore
        }
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableDefinitionIdentifier.class, areImmutable(),
                provided(URL.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableDefinitionIdentifier.class)
                .usingGetClass()
                .withIgnoredFields("stringRepresentation") // as it is only derived from other properties
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullNamespace() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableDefinitionIdentifier.getInstance(null, NAME, VERSION))
                .withMessage("The %s must not be null!", "namespace")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithEmptyNamespace() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ImmutableDefinitionIdentifier.getInstance("", NAME, VERSION))
                .withMessage("The argument '%s' must not be empty!", "namespace")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullName() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableDefinitionIdentifier.getInstance(NAMESPACE, null, VERSION))
                .withMessage("The %s must not be null!", "name")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithEmptyName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ImmutableDefinitionIdentifier.getInstance(NAMESPACE, "", VERSION))
                .withMessage("The argument '%s' must not be empty!", "name")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullVersion() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableDefinitionIdentifier.getInstance(NAMESPACE, NAME, null))
                .withMessage("The %s must not be null!", "version")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithEmptyVersion() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ImmutableDefinitionIdentifier.getInstance(NAMESPACE, NAME, ""))
                .withMessage("The argument '%s' must not be empty!", "version")
                .withNoCause();
    }

    @Test
    public void getNamespaceReturnsExpected() {
        final DefinitionIdentifier underTest =
                ImmutableDefinitionIdentifier.getInstance(NAMESPACE, NAME, VERSION);

        assertThat(underTest.getNamespace()).isEqualTo(NAMESPACE);
    }

    @Test
    public void getNameReturnsExpected() {
        final DefinitionIdentifier underTest =
                ImmutableDefinitionIdentifier.getInstance(NAMESPACE, NAME, VERSION);

        assertThat(underTest.getName()).isEqualTo(NAME);
    }

    @Test
    public void getVersionReturnsExpected() {
        final DefinitionIdentifier underTest =
                ImmutableDefinitionIdentifier.getInstance(NAMESPACE, NAME, VERSION);

        assertThat(underTest.getVersion()).isEqualTo(VERSION);
    }

    @Test
    public void getUrlReturnsExpected() {
        final DefinitionIdentifier underTest =
                ImmutableDefinitionIdentifier.getInstance(VALID_IDENTIFIER_URL);

        assertThat(underTest.getUrl()).contains(VALID_IDENTIFIER_URL);
    }

    @Test
    public void toStringReturnsExpected() {
        final DefinitionIdentifier underTest =
                ImmutableDefinitionIdentifier.getInstance(NAMESPACE, NAME, VERSION);

        assertThat(underTest.toString()).hasToString(VALID_IDENTIFIER_STRING);
    }

    @Test
    public void toStringWithUrlReturnsExpected() {
        final DefinitionIdentifier underTest =
                ImmutableDefinitionIdentifier.getInstance(VALID_IDENTIFIER_URL);

        assertThat(underTest.toString()).hasToString(VALID_IDENTIFIER_URL_STR);
    }

    @Test
    public void tryToParseNullIdentifierString() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableDefinitionIdentifier.ofParsed(null))
                .withMessage("The %s must not be null!", "CharSequence-representation of the identifier")
                .withNoCause();
    }

    @Test
    public void tryToParseEmptyIdentifierString() {
        assertThatExceptionOfType(DefinitionIdentifierInvalidException.class)
                .isThrownBy(() -> ImmutableDefinitionIdentifier.ofParsed(""))
                .withMessage("Definition identifier <> is invalid!")
                .withNoCause();
    }

    @Test
    public void tryToParseIdentifierStringWithEmptyNameSegment() {
        final String invalidString = NAMESPACE + "::" + VERSION;

        assertThatExceptionOfType(DefinitionIdentifierInvalidException.class)
                .isThrownBy(() -> ImmutableDefinitionIdentifier.ofParsed(invalidString))
                .withMessage("Definition identifier <%s> is invalid!", invalidString)
                .withNoCause();
    }

    @Test
    public void tryToParseIdentifierStringWithIllegalChar() {
        final String invalidString = "org/eclipse/ditto" + ":" + NAME + ":" + VERSION;

        assertThatExceptionOfType(DefinitionIdentifierInvalidException.class)
                .isThrownBy(() -> ImmutableDefinitionIdentifier.ofParsed(invalidString))
                .withMessage("Definition identifier <%s> is invalid!", invalidString)
                .withNoCause();
    }

    @Test
    public void parseValidIdentifierString() {
        final DefinitionIdentifier actual =
                ImmutableDefinitionIdentifier.ofParsed(VALID_IDENTIFIER_STRING);

        final DefinitionIdentifier expected =
                ImmutableDefinitionIdentifier.getInstance(NAMESPACE, NAME, VERSION);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void parseValidIdentifierUrlString() {
        final DefinitionIdentifier actual =
                ImmutableDefinitionIdentifier.ofParsed(VALID_IDENTIFIER_URL_STR);

        assertThat(actual.getNamespace()).isEmpty();
        assertThat(actual.getName()).isEmpty();
        assertThat(actual.getVersion()).isEmpty();
        assertThat(actual.getUrl()).contains(VALID_IDENTIFIER_URL);

        final DefinitionIdentifier expected =
                ImmutableDefinitionIdentifier.getInstance(VALID_IDENTIFIER_URL);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void tryToParseIdentifierUrlStringWithMalformedUrl() {
        final String invalidString = "org/eclipse/ditto" + ":" + NAME + ":" + VERSION;

        assertThatExceptionOfType(DefinitionIdentifierInvalidException.class)
                .isThrownBy(() -> ImmutableDefinitionIdentifier.ofParsed(invalidString))
                .withMessage("Definition identifier <%s> is invalid!", invalidString)
                .withNoCause();
    }

}
