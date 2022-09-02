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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.json.JsonArray;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.JsonArrayValueValidator}.
 */
public final class JsonArrayValueValidatorTest {

    private static CharSequence validJsonArrayString;

    @BeforeClass
    public static void setUpClass() {
        final JsonArray validJsonArray = JsonArray.newBuilder()
                .add("foo", "bar", "baz")
                .build();
        validJsonArrayString = validJsonArray.toString();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonArrayValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        final JsonArrayValueValidator underTest = JsonArrayValueValidator.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, validJsonArrayString))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.REQUESTED_ACKS;
        final JsonArrayValueValidator underTest = JsonArrayValueValidator.getInstance();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, null))
                .withMessageContaining("null")
                .withMessageContaining(headerDefinition.getKey())
                .withMessageEndingWith("is not a valid JsonArray.")
                .withNoCause();
    }

    @Test
    public void acceptValidCharSequence() {
        final JsonArrayValueValidator underTest = JsonArrayValueValidator.getInstance();

        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.REQUESTED_ACKS, validJsonArrayString))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotJsonArray() {
        final JsonArrayValueValidator underTest = JsonArrayValueValidator.getInstance();

        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, validJsonArrayString))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptNonJsonArrayString() {
        final String invalidJsonArrayString = "foo";
        final JsonArrayValueValidator underTest = JsonArrayValueValidator.getInstance();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.REQUESTED_ACKS, invalidJsonArrayString))
                .withMessageContaining(invalidJsonArrayString)
                .withMessageEndingWith("is not a valid JSON array.")
                .withNoCause();
    }

    @Test
    public void acceptJsonArrayStringWithNonStringItems() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.REQUESTED_ACKS;
        final JsonArray invalidJsonArray = JsonArray.newBuilder()
                .add("foo")
                .add(42)
                .add(true)
                .build();
        final String invalidJsonArrayString = invalidJsonArray.toString();
        final JsonArrayValueValidator underTest = JsonArrayValueValidator.getInstance();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, invalidJsonArrayString))
                .withMessageContaining(headerDefinition.getKey())
                .withMessageEndingWith("contained non-string values!")
                .withNoCause();
    }

}
