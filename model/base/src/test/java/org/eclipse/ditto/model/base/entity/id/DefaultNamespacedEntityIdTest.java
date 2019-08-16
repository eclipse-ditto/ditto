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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class DefaultNamespacedEntityIdTest {

    private static final List<String> ALLOWED_SPECIAL_CHARACTERS_IN_NAME_AND_VERSION =
            Arrays.asList("-", "@", "&", "=", "+", ",", ".", "!", "~", "*", "'", "$", "_", ";", "%3A", "<", ">");

    @Test
    public void valid() {
        final NamespacedEntityId namespacedEntityId =
                DefaultNamespacedEntityId.of("ns", "58b4d0e9-2e97-498e-a49b-470cca589c3c:<anonymous>");

        assertThat(namespacedEntityId.getNamespace()).isEqualTo("ns");
        assertThat(namespacedEntityId.getName()).isEqualTo("58b4d0e9-2e97-498e-a49b-470cca589c3c:<anonymous>");
    }

    @Test
    public void doubleColon() {
        final NamespacedEntityId namespacedEntityId = DefaultNamespacedEntityId.of("ns::name");

        assertThat(namespacedEntityId.getNamespace()).isEqualTo("ns");
        assertThat(namespacedEntityId.getName()).isEqualTo(":name");
    }

    @Test
    public void emptyNamespace() {
        final NamespacedEntityId namespacedEntityId = DefaultNamespacedEntityId.of(":name");

        assertThat(namespacedEntityId.getNamespace()).isEmpty();
        assertThat(namespacedEntityId.getName()).isEqualTo("name");
    }

    @Test
    public void onlyColons() {
        final NamespacedEntityId namespacedEntityId = DefaultNamespacedEntityId.of("::");

        assertThat(namespacedEntityId.getNamespace()).isEmpty();
        assertThat(namespacedEntityId.getName()).isEqualTo(":");
    }

    @Test
    public void withValidSpecialCharactersInName() {
        ALLOWED_SPECIAL_CHARACTERS_IN_NAME_AND_VERSION.forEach((specialCharacter) -> {
            assertThatCode(() -> DefaultNamespacedEntityId.of("ns:x" + specialCharacter))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    public void DollarSymbolNotAllowedAtBeginningOfName() {
        assertThatExceptionOfType(EntityNameInvalidException.class)
                .isThrownBy(() -> DefaultNamespacedEntityId.of("ns", "$"));
    }

    @Test
    public void numbersAfterDotInNamespacedIsNotAllowed() {

        assertThatExceptionOfType(EntityNamespaceInvalidException.class)
                .isThrownBy(() -> DefaultNamespacedEntityId.of("notMyNamespace.6", "policy-id"));
    }

}