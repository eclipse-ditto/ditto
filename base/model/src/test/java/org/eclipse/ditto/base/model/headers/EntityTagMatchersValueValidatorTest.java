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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.EntityTagMatchersValueValidator}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class EntityTagMatchersValueValidatorTest {

    private static CharSequence validEntityTagMatchersString;

    @Mock private HeaderDefinition entityTagMatchersHeaderDefinition;
    private EntityTagMatchersValueValidator underTest;

    @BeforeClass
    public static void setUpClass() {
        validEntityTagMatchersString = "\"foo\",\"bar\"";
    }

    @Before
    public void setUp() {
        Mockito.when(entityTagMatchersHeaderDefinition.getKey()).thenReturn("my-entity-tag-matchers");
        Mockito.when(entityTagMatchersHeaderDefinition.getJavaType()).thenReturn(EntityTagMatchers.class);
        underTest = EntityTagMatchersValueValidator.getInstance(
                EntityTagMatcherValueValidator.getInstance(EntityTagMatchers.class::equals));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityTagMatchersValueValidator.class,
                areImmutable(),
                provided(ValueValidator.class).isAlsoImmutable());
    }

    @Test
    public void tryToGetInstanceWithNullEntityTagMatcherValueValidator() {
        assertThatNullPointerException()
                .isThrownBy(() -> EntityTagMatchersValueValidator.getInstance(null))
                .withMessage("The entityTagMatcherValueValidator must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, validEntityTagMatchersString))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(entityTagMatchersHeaderDefinition, null))
                .withMessageContaining("null")
                .withMessageContaining(entityTagMatchersHeaderDefinition.getKey())
                .withMessageEndingWith("is not a valid EntityTagMatchers.")
                .withNoCause();
    }

    @Test
    public void acceptValidCharSequence() {
        assertThatCode(() -> underTest.accept(entityTagMatchersHeaderDefinition, validEntityTagMatchersString))
                .doesNotThrowAnyException();
    }

    @Test
    public void canValidateEntityTagMatchers() {
        assertThat(underTest.canValidate(EntityTagMatchers.class)).isTrue();
    }

    @Test
    public void acceptValidCharSequenceWithSpaces() {
        assertThatCode(() -> underTest.accept(entityTagMatchersHeaderDefinition, "\"foo\", \"bar\""))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotEntityTagMatchers() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, validEntityTagMatchersString))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptNonEntityTagMatchersString() {
        final String invalidEntityTagMatchersString = "true";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(entityTagMatchersHeaderDefinition, invalidEntityTagMatchersString))
                .withMessageContaining(invalidEntityTagMatchersString)
                .withMessageEndingWith("is not a valid entity-tag.")
                .withNoCause();
    }

    @Test
    public void acceptInvalidEntityTagMatchersString() {
        final String invalidEntityTagMatchersString = "1h";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(entityTagMatchersHeaderDefinition, invalidEntityTagMatchersString))
                .withMessageContaining(invalidEntityTagMatchersString)
                .withMessageEndingWith("is not a valid entity-tag.")
                .satisfies(e -> assertThat(e.getHref())
                        .hasValue(URI.create(EntityTagValueValidator.RFC_7232_SECTION_2_3)))
                .withNoCause();
    }

}
