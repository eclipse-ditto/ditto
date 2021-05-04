/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.EntityTagMatcherValueValidator}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class EntityTagMatcherValueValidatorTest {

    private static CharSequence validEntityTagMatcherString;

    @Mock private HeaderDefinition entityTagMatcherHeaderDefinition;
    private EntityTagMatcherValueValidator underTest;

    @BeforeClass
    public static void setUpClass() {
        final EntityTagMatcher entityTag = EntityTagMatcher.fromString("W/\"1\"");
        validEntityTagMatcherString = entityTag.toString();
    }

    @Before
    public void setUp() {
        Mockito.when(entityTagMatcherHeaderDefinition.getKey()).thenReturn("my-entity-tag-matcher");
        Mockito.when(entityTagMatcherHeaderDefinition.getJavaType()).thenReturn(EntityTagMatcher.class);
        underTest = EntityTagMatcherValueValidator.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityTagMatcherValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, validEntityTagMatcherString))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(entityTagMatcherHeaderDefinition, null))
                .withMessageContaining("null")
                .withMessageContaining(entityTagMatcherHeaderDefinition.getKey())
                .withMessageEndingWith("is not a valid EntityTagMatcher.")
                .withNoCause();
    }

    @Test
    public void canValidateEntityTagMatchers() {
        assertThat(underTest.canValidate(EntityTagMatcher.class)).isTrue();
    }

    @Test
    public void acceptValidCharSequence() {
        assertThatCode(() -> underTest.accept(entityTagMatcherHeaderDefinition, validEntityTagMatcherString))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotEntityTagMatcher() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, validEntityTagMatcherString))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptNonEntityTagMatcherString() {
        final String invalidEntityTagMatcherString = "true";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(entityTagMatcherHeaderDefinition, invalidEntityTagMatcherString))
                .withMessageContaining(invalidEntityTagMatcherString)
                .withMessageEndingWith("is not a valid entity-tag.")
                .withNoCause();
    }

    @Test
    public void acceptInvalidEntityTagMatcherString() {
        final String invalidEntityTagMatcherString = "1h";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(entityTagMatcherHeaderDefinition, invalidEntityTagMatcherString))
                .withMessageContaining(invalidEntityTagMatcherString)
                .withMessageEndingWith("is not a valid entity-tag.")
                .satisfies(e -> assertThat(e.getHref())
                        .hasValue(URI.create(EntityTagValueValidator.RFC_7232_SECTION_2_3)))
                .withNoCause();
    }

}
