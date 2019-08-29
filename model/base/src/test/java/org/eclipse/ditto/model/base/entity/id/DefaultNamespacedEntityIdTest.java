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
package org.eclipse.ditto.model.base.entity.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class DefaultNamespacedEntityIdTest {

    private static final String URL_ESCAPE_EXAMPLE = "%3A";
    private static final List<String> ALLOWED_SPECIAL_CHARACTERS_IN_NAME = Arrays.asList(
            "-", "@", "&", "=", "+", ",", ".", "!", "~", "*", "'", "$", "_", ";", URL_ESCAPE_EXAMPLE, "<", ">"
    );

    private static final String VALID_NAMESPACE = "validNamespace";
    private static final String NAMESPACE_DELIMITER = ":";
    private static final String VALID_NAME = "validName";
    private static final String VALID_ID = VALID_NAMESPACE + NAMESPACE_DELIMITER + VALID_NAME;

    @Test
    public void testImmutability() {
        assertInstancesOf(DefaultNamespacedEntityId.class, areImmutable());
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(DefaultNamespacedEntityId.class).withIgnoredFields("stringRepresentation").verify();
    }

    @Test
    public void fromNameHasEmptyNamespace() {
        final NamespacedEntityId namespacedEntityId = DefaultNamespacedEntityId.fromName(VALID_NAME);
        assertThat(namespacedEntityId.getNamespace()).isEmpty();
    }

    @Test
    public void defaultNamespacedEntityIdFromDefaultNamespacedEntityIdIsSameInstance() {
        final NamespacedEntityId namespacedEntityIdOne = DefaultNamespacedEntityId.of(VALID_NAMESPACE, VALID_NAME);
        final NamespacedEntityId namespacedEntityIdTwo = DefaultNamespacedEntityId.of(namespacedEntityIdOne);
        assertThat((CharSequence) namespacedEntityIdOne).isSameAs(namespacedEntityIdTwo);
    }

    @Test
    public void defaultNamespacedEntityIdFromNamespacedEntityIdSkipsValidation() {
        /*
         * NEVER DO SUCH HACKS, PLEASE! The only purpose of this validation skip is to save performance because we
         * can trust our own code to not implement any NamespacedEntityId without validating namespaces and names.
         */

        final String invalidNamespace = ".invalidNamespace";
        final String invalidName = "§invalidName";
        final NamespacedEntityId invalidNamespacedEntityId = new NamespacedEntityId() {

            @Override
            public boolean isDummy() {
                return false;
            }

            @Override
            public String getName() {
                return invalidName;
            }

            @Override
            public String getNamespace() {
                return invalidNamespace;
            }
        };

        final NamespacedEntityId namespacedEntityId = DefaultNamespacedEntityId.of(invalidNamespacedEntityId);

        assertThat(namespacedEntityId.getNamespace()).isEqualTo(invalidNamespace);
        assertThat(namespacedEntityId.getName()).isEqualTo(invalidName);
    }

    @Test
    public void nullId() {
        assertThatExceptionOfType(NamespacedEntityIdInvalidException.class)
                .isThrownBy(() -> DefaultNamespacedEntityId.of(null));
    }

    @Test
    public void nullName() {
        assertInvalidName(null);
    }

    @Test
    public void nullNamespace() {
        assertThatExceptionOfType(NamespacedEntityIdInvalidException.class)
                .isThrownBy(() -> DefaultNamespacedEntityId.of(null, VALID_NAME));
    }

    @Test
    public void testConstantsAreValid() {
        assertValidNamespace(VALID_NAMESPACE);
        assertValidName(VALID_NAME);
    }

    @Test
    public void toStringConcatenatesNamespaceAndName() {
        assertThat(DefaultNamespacedEntityId.of(VALID_NAMESPACE, VALID_NAME).toString()).isEqualTo(VALID_ID);
        assertThat(DefaultNamespacedEntityId.of(VALID_ID).toString()).isEqualTo(VALID_ID);
    }

    @Test
    public void placeholderIsPlaceholder() {
        assertThat(DefaultNamespacedEntityId.dummy().isDummy()).isTrue();
    }

    @Test
    public void manuallyCreatedPlaceholderIsPlaceholder() {
        assertThat(DefaultNamespacedEntityId.of(":_").isDummy()).isTrue();
        assertThat(DefaultNamespacedEntityId.of("", "_").isDummy()).isTrue();
    }

    @Test
    public void valid() {
        assertValidId("ns", "58b4d0e9-2e97-498e-a49b-470cca589c3c:<anonymous>");
    }

    @Test
    public void nameStartsWithColon() {
        assertValidName(":name");
    }

    @Test
    public void emptyNamespace() {
        assertValidId("", "name");
    }

    @Test
    public void onlyColons() {
        assertValidId("", ":::");
    }

    @Test
    public void withValidSpecialCharactersInName() {
        ALLOWED_SPECIAL_CHARACTERS_IN_NAME.forEach((specialCharacter) -> {
            assertValidName("x" + specialCharacter);
        });
    }

    @Test
    public void dollarSymbolNotAllowedAtBeginningOfName() {
        assertInvalidName("$foo");
    }

    @Test
    public void percentSymbolNotAllowedIfNotURLEscaping() {
        assertInvalidName("fo%o");
    }

    @Test
    public void paragraphSymbolNotAllowed() {
        assertInvalidName("f§oo");
    }

    @Test
    public void numbersAfterDotInNamespacedIsNotAllowed() {
        assertInvalidNamespace("ns.x.5");
        System.out.println(RegexPatterns.ID_REGEX);
    }

    @Test
    public void namespacesWithLeadingDotAreInvalid() {
        assertInvalidNamespace(".ns");
    }

    @Test
    public void numbersInNamespacesAreAllowed() {
        assertValidNamespace("ns5.foo23.bar2");
    }

    @Test
    public void mutliplePrecedingDotsInNamespaceAreNotAllowed() {
        assertInvalidNamespace("my....namespace");
    }

    @Test
    public void notSpecialCharactersInNamespacesAreAllowed() {
        assertInvalidNamespace("my$namespace");
    }

    @Test
    public void validateLongIdSucceeds() {
        final int numberOfUUIDs = 1000;
        final StringBuilder idBuilder = new StringBuilder("namespace");
        for (int i = 0; i < numberOfUUIDs; ++i) {
            idBuilder.append(':').append(UUID.randomUUID());
        }

        final NamespacedEntityId namespacedEntityId = DefaultNamespacedEntityId.of(idBuilder.toString());

        assertThat(namespacedEntityId.getNamespace()).isEqualTo("namespace");
    }

    private static void assertInvalidNamespace(@Nullable final String namespace) {
        assertInValidId(namespace, VALID_NAME);
    }

    private static void assertValidNamespace(@Nullable final String namespace) {
        assertValidId(namespace, VALID_NAME);
    }

    private static void assertInvalidName(@Nullable final String name) {
        assertInValidId(VALID_NAMESPACE, name);
    }

    private static void assertValidName(@Nullable final String name) {
        assertValidId(VALID_NAMESPACE, name);
    }

    private static void assertValidId(@Nullable final String namespace, @Nullable final String name) {
        final NamespacedEntityId idBySeparated = DefaultNamespacedEntityId.of(namespace, name);
        assertThat(idBySeparated.getNamespace()).isEqualTo(namespace);
        assertThat(idBySeparated.getName()).isEqualTo(name);
        assertThat(idBySeparated.isDummy()).isFalse();

        final NamespacedEntityId idByCombined =
                DefaultNamespacedEntityId.of(concatenateNamespaceAndName(namespace, name));
        assertThat(idByCombined.getNamespace()).isEqualTo(namespace);
        assertThat(idByCombined.getName()).isEqualTo(name);
        assertThat(idByCombined.isDummy()).isFalse();
    }

    private static void assertInValidId(@Nullable final String namespace, @Nullable final String name) {

        assertThatExceptionOfType(NamespacedEntityIdInvalidException.class)
                .isThrownBy(() -> DefaultNamespacedEntityId.of(concatenateNamespaceAndName(namespace, name)));

        assertThatExceptionOfType(NamespacedEntityIdInvalidException.class)
                .isThrownBy(() -> DefaultNamespacedEntityId.of(namespace, name));
    }

    private static String concatenateNamespaceAndName(@Nullable final String namespace, @Nullable final String name) {
        final String nonNullNamespace = namespace == null ? "" : namespace;
        final String nonNullName = name == null ? "" : name;

        return nonNullNamespace + NAMESPACE_DELIMITER + nonNullName;
    }

}