/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.base.headers;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.fail;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonParseException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link HeaderValueValidatorTest}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class HeaderValueValidatorTest {

    private static final String HEADER_KEY = "header-key";

    @Mock
    private HeaderDefinition headerDefinition;

    private HeaderValueValidator underTest;

    @Test
    public void assertImmutability() {
        assertInstancesOf(HeaderValueValidator.class, areImmutable());
    }

    @Before
    public void setUp() {
        Mockito.when(headerDefinition.getKey()).thenReturn(HEADER_KEY);
        underTest = HeaderValueValidator.getInstance();
    }

    @Test
    public void validBooleanStringValue() {
        Mockito.when(headerDefinition.getJavaType()).thenReturn(boolean.class);
        final String value = String.valueOf(Boolean.TRUE);

        try {
            underTest.accept(headerDefinition, value);
        } catch (final Throwable t) {
            fail(value + " is a valid boolean representation!");
        }
    }

    @Test
    public void invalidBooleanStringValue() {
        Mockito.when(headerDefinition.getJavaType()).thenReturn(Boolean.class);
        final String value = "foo";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, value))
                .withMessage("Value <%s> for key <%s> is not a valid boolean!", value, HEADER_KEY)
                .withNoCause();
    }

    @Test
    public void validIntStringValue() {
        Mockito.when(headerDefinition.getJavaType()).thenReturn(int.class);
        final String value = String.valueOf(42);

        try {
            underTest.accept(headerDefinition, value);
        } catch (final Throwable t) {
            fail(value + " is a valid int representation!");
        }
    }

    @Test
    public void invalidIntStringValue() {
        Mockito.when(headerDefinition.getJavaType()).thenReturn(Integer.class);
        final String value = "foo";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, value))
                .withMessage("Value <%s> for key <%s> is not a valid int!", value, HEADER_KEY)
                .withCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    public void validJsonArrayStringValue() {
        Mockito.when(headerDefinition.getJavaType()).thenReturn(JsonArray.class);
        final JsonArray jsonArray = JsonFactory.newArrayBuilder()
                .add("foo")
                .add("bar")
                .add("baz")
                .build();
        final String value = jsonArray.toString();

        try {
            underTest.accept(headerDefinition, value);
        } catch (final Throwable t) {
            fail(value + " is a valid JsonArray representation!");
        }
    }

    @Test
    public void invalidJsonArrayStringValueNoJsonArray() {
        Mockito.when(headerDefinition.getJavaType()).thenReturn(JsonArray.class);
        final String value = "foo";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, value))
                .withMessage("Value <%s> for key <%s> is not a valid JSON array!", value, HEADER_KEY)
                .withCauseInstanceOf(JsonParseException.class);
    }

    @Test
    public void invalidJsonArrayStringValueNoStringItem() {
        Mockito.when(headerDefinition.getJavaType()).thenReturn(JsonArray.class);
        final JsonArray jsonArray = JsonFactory.newArrayBuilder()
                .add("foo")
                .add(2)
                .add("baz")
                .build();
        final String value = jsonArray.toString();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, value))
                .withMessage("Value <%s> for key <%s> is not a valid JSON array!", value, HEADER_KEY)
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

}
