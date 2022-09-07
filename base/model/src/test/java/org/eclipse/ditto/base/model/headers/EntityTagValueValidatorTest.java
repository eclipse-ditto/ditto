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
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.EntityTagValueValidator}.
 */
public final class EntityTagValueValidatorTest {

    private static CharSequence validEntityTagString;

    private EntityTagValueValidator underTest;

    @BeforeClass
    public static void setUpClass() {
        final EntityTag entityTag = EntityTag.fromString("\"-12124212\"");
        validEntityTagString = entityTag.toString();
    }

    @Before
    public void setUp() {
        underTest = EntityTagValueValidator.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityTagValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, validEntityTagString))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.ETAG;

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, null))
                .withMessageContaining("null")
                .withMessageContaining(headerDefinition.getKey())
                .withMessageEndingWith("is not a valid EntityTag.")
                .withNoCause();
    }

    @Test
    public void canValidateEntityTag() {
        assertThat(underTest.canValidate(EntityTag.class)).isTrue();
    }

    @Test
    public void acceptValidCharSequence() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.ETAG, validEntityTagString))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotEntityTag() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, validEntityTagString))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptNonEntityTagString() {
        final String invalidEntityTagString = "true";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.ETAG, invalidEntityTagString))
                .withMessageContaining(invalidEntityTagString)
                .withMessageEndingWith("is not a valid entity-tag.")
                .withNoCause();
    }

    @Test
    public void acceptInvalidEntityTagString() {
        final String invalidEntityTagString = "1h";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.ETAG, invalidEntityTagString))
                .withMessageContaining(invalidEntityTagString)
                .withMessageEndingWith("is not a valid entity-tag.")
                .satisfies(e -> assertThat(e.getHref())
                        .hasValue(URI.create(EntityTagValueValidator.RFC_7232_SECTION_2_3)))
                .withNoCause();
    }

}
