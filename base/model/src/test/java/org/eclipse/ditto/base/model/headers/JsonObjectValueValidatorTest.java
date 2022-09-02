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
import org.eclipse.ditto.json.JsonObject;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.JsonObjectValueValidator}.
 */
public final class JsonObjectValueValidatorTest {

    private static CharSequence validJsonObjectString;

    @BeforeClass
    public static void setUpClass() {
        final JsonObject validJsonObject = JsonObject.newBuilder()
                .set("foo", "bar")
                .set("one", 2)
                .build();
        validJsonObjectString = validJsonObject.toString();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonObjectValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        final JsonObjectValueValidator underTest = JsonObjectValueValidator.getInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, validJsonObjectString))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.AUTHORIZATION_CONTEXT;
        final JsonObjectValueValidator underTest = JsonObjectValueValidator.getInstance();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, null))
                .withMessageContaining("null")
                .withMessageContaining(headerDefinition.getKey())
                .withMessageEndingWith("is not a valid JsonObject.")
                .withNoCause();
    }

    @Test
    public void acceptValidCharSequence() {
        final JsonObjectValueValidator underTest = JsonObjectValueValidator.getInstance();

        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.AUTHORIZATION_CONTEXT, validJsonObjectString))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotJsonObject() {
        final JsonObjectValueValidator underTest = JsonObjectValueValidator.getInstance();

        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, validJsonObjectString))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptNonJsonObjectString() {
        final String invalidJsonObjectString = "foo";
        final JsonObjectValueValidator underTest = JsonObjectValueValidator.getInstance();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.AUTHORIZATION_CONTEXT, invalidJsonObjectString))
                .withMessageContaining(invalidJsonObjectString)
                .withMessageEndingWith("is not a valid JSON object.")
                .withNoCause();
    }

}
