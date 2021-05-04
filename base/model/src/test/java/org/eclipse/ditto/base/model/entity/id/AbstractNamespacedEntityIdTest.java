/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.entity.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.restriction.LengthRestrictionTestBase;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.entity.id.AbstractNamespacedEntityId}.
 * It uses a dummy implementation as this class only tests commonly implemented methods of NamespacedEntityId.
 */
@RunWith(Enclosed.class)
public final class AbstractNamespacedEntityIdTest extends LengthRestrictionTestBase {

    private static final EntityType ENTITY_TYPE_PLUMBUS = EntityType.of("plumbus");

    private static final String URL_ESCAPE_EXAMPLE = "%3A";
    private static final List<String> ALLOWED_SPECIAL_CHARACTERS_IN_NAME = Arrays.asList(
            "-", "@", "&", "=", "+", ",", ".", "!", "~", "*", "'", "$", "_", ";", URL_ESCAPE_EXAMPLE, "<", ">"
    );

    private static final String NAMESPACE_DELIMITER = ":";
    private static final String VALID_NAMESPACE = "namespace";
    private static final String VALID_NAME = "name";
    private static final String VALID_ID = VALID_NAMESPACE + NAMESPACE_DELIMITER + VALID_NAME;

    public static final class GeneralFunctionalityTest {

        @Test
        public void testEqualsAndHashcode() {
            EqualsVerifier.forClass(AbstractNamespacedEntityId.class)
                    .withRedefinedSuperclass()
                    .usingGetClass()
                    //already contained in string representation which is compared in base class
                    .withIgnoredFields("name", "namespace")
                    .verify();
        }

        @Test
        public void canHaveMaximumLengthOf256Characters() {
            assertValidId(VALID_NAMESPACE, generateStringWithLength(MAX_LENGTH - VALID_NAMESPACE.length() - 1));
        }

        @Test
        public void cannotHaveMoreThan256Characters() {
            assertInValidId(VALID_NAMESPACE, generateStringWithLength(MAX_LENGTH - VALID_NAMESPACE.length()));
        }

        @Test
        public void nullId() {
            assertThatExceptionOfType(NamespacedEntityIdInvalidException.class)
                    .isThrownBy(() -> DummyImplementation.of(ENTITY_TYPE_PLUMBUS, null));
        }

        @Test
        public void nullName() {
            assertInvalidName(null);
        }

        @Test
        public void nullNamespace() {
            assertThatExceptionOfType(NamespacedEntityIdInvalidException.class)
                    .isThrownBy(() -> DummyImplementation.of(ENTITY_TYPE_PLUMBUS, null, VALID_NAME));
        }

        @Test
        public void testConstantsAreValid() {
            assertValidNamespace(VALID_NAMESPACE);
            assertValidName(VALID_NAME);
        }

        @Test
        public void toStringConcatenatesNamespaceAndName() {
            assertThat(DummyImplementation.of(ENTITY_TYPE_PLUMBUS, VALID_NAMESPACE, VALID_NAME).toString())
                    .hasToString(VALID_ID);
            assertThat(DummyImplementation.of(ENTITY_TYPE_PLUMBUS, VALID_ID).toString()).hasToString(VALID_ID);
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
        public void paragraphSymbolNotAllowed() {
            assertInvalidName("f§oo");
        }

        @Test
        public void numbersAfterDotInNamespacedIsNotAllowed() {
            assertInvalidNamespace("ns.x.5");
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
        public void multiplePrecedingDotsInNamespaceAreNotAllowed() {
            assertInvalidNamespace("my....namespace");
        }

        @Test
        public void notSpecialCharactersInNamespacesAreAllowed() {
            assertInvalidNamespace("my$namespace");
        }

        private static void assertInvalidNamespace(@Nullable final String namespace) {
            assertInValidId(namespace, VALID_NAME);
        }

        private void assertValidNamespace(@Nullable final String namespace) {
            assertValidId(namespace, VALID_NAME);
        }

        private static void assertInvalidName(@Nullable final String name) {
            assertInValidId(VALID_NAMESPACE, name);
        }

        private static void assertValidName(@Nullable final String name) {
            assertValidId(VALID_NAMESPACE, name);
        }

        private static void assertValidId(@Nullable final String namespace, @Nullable final String name) {
            final NamespacedEntityId idBySeparated = DummyImplementation.of(ENTITY_TYPE_PLUMBUS, namespace, name);
            assertThat(idBySeparated.getNamespace()).isEqualTo(namespace);
            assertThat(idBySeparated.getName()).isEqualTo(name);

            final NamespacedEntityId idByCombined =
                    DummyImplementation.of(ENTITY_TYPE_PLUMBUS, concatenateNamespaceAndName(namespace, name));
            assertThat(idByCombined.getNamespace()).isEqualTo(namespace);
            assertThat(idByCombined.getName()).isEqualTo(name);
        }

        private static void assertInValidId(@Nullable final String namespace, @Nullable final String name) {

            final String concatenateNamespaceAndName = concatenateNamespaceAndName(namespace, name);
            assertThatExceptionOfType(NamespacedEntityIdInvalidException.class)
                    .isThrownBy(() -> DummyImplementation.of(ENTITY_TYPE_PLUMBUS, concatenateNamespaceAndName));

            assertThatExceptionOfType(NamespacedEntityIdInvalidException.class)
                    .isThrownBy(() -> DummyImplementation.of(ENTITY_TYPE_PLUMBUS, namespace, name));
        }

        private static String concatenateNamespaceAndName(@Nullable final String namespace,
                @Nullable final String name) {
            final String nonNullNamespace = namespace == null ? "" : namespace;
            final String nonNullName = name == null ? "" : name;

            return nonNullNamespace + NAMESPACE_DELIMITER + nonNullName;
        }

    }

    private static final class DummyImplementation extends AbstractNamespacedEntityId {

        private DummyImplementation(final EntityType entityType, final String namespace, final String name) {
            super(entityType, namespace, name, true);
        }

        private DummyImplementation(final EntityType entityType, final CharSequence entityId) {
            super(entityType, entityId);
        }

        static DummyImplementation of(final EntityType entityType, final CharSequence entityId) {
            return new DummyImplementation(entityType, entityId);
        }

        static DummyImplementation of(final EntityType entityType, @Nullable final String namespace,
                final String name) {

            final NamespacedEntityId namespacedEntityId;
            if (null == namespace) {
                namespacedEntityId = new DummyImplementation(entityType, name);
            } else {
                namespacedEntityId = new DummyImplementation(entityType, namespace, name);
            }
            return new DummyImplementation(entityType, namespacedEntityId);
        }

    }

}
