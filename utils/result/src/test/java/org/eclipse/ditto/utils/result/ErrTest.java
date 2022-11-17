/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.utils.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Err}.
 */
public final class ErrTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(Err.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> Err.of(null))
                .withMessage("value")
                .withNoCause();
    }

    @Test
    public void getErrorValueReturnsExpectedValue() {
        final var value = new Object();
        final var underTest = Err.of(value);

        assertThat(underTest.getErrorValue()).isEqualTo(value);
    }

    @Test
    public void isOkReturnsFalse() {
        final var underTest = Err.of(new Object());

        assertThat(underTest.isOk()).isFalse();
    }

    @Test
    public void isErrReturnsTrue() {
        final var underTest = Err.of(new Object());

        assertThat(underTest.isErr()).isTrue();
    }

    @Test
    public void ifOkDoesNotApplySuccessValueAction() {
        final var value = new Object();
        final Consumer<Object> errorValueAction = Mockito.mock(Consumer.class);
        final var underTest = Err.of(value);

        underTest.ifOk(errorValueAction);

        Mockito.verifyNoInteractions(errorValueAction);
    }

    @Test
    public void ifErrWithNullThrowsNullPointerException() {
        final var underTest = Err.of(new Object());

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.ifErr(null))
                .withMessage("errorValueAction")
                .withNoCause();
    }

    @Test
    public void ifErrAppliesSpecifiedErrorValueAction() {
        final var value = new Object();
        final Consumer<Object> errorValueAction = Mockito.mock(Consumer.class);
        final var underTest = Err.of(value);

        underTest.ifErr(errorValueAction);

        Mockito.verify(errorValueAction).accept(Mockito.eq(value));
    }

    @Test
    public void okReturnsEmptyOptional() {
        final var underTest = Err.of(new Object());

        assertThat(underTest.ok()).isEmpty();
    }

    @Test
    public void orElseReturnsAlternativeSuccessValue() {
        final var alternativeSuccessValue = new Object();
        final var underTest = Err.of(new Object());

        assertThat(underTest.orElse(alternativeSuccessValue)).isEqualTo(alternativeSuccessValue);
    }

    @Test
    public void orElseGetReturnsAppliesSupplyingFunction() {
        final var alternativeSuccessValue = new Object();
        final var underTest = Err.of(new Object());

        assertThat(underTest.orElseGet(() -> alternativeSuccessValue)).isEqualTo(alternativeSuccessValue);
    }

    @Test
    public void orElseThrowThrowsValueIfRuntimeException() {
        final var value = new IllegalStateException("Bam!");
        final var underTest = Err.of(value);

        assertThatThrownBy(underTest::orElseThrow).isEqualTo(value);
    }

    @Test
    public void orElseThrowThrowsValueIfError() {
        final var value = new Error("Oh oh!");
        final var underTest = Err.of(value);

        assertThatThrownBy(underTest::orElseThrow).isEqualTo(value);
    }

    @Test
    public void orElseThrowThrowsNoSuchElementExceptionWrappingValueIfCheckedException() {
        final var value = new Exception("Ouch");
        final var underTest = Err.of(value);

        assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(underTest::orElseThrow)
                .withMessage(value.getMessage())
                .withCause(value);
    }

    @Test
    public void orElseThrowsThrowsGenericNoSuchElementExceptionIfValueIsNoThrowable() {
        final var underTest = Err.of("myValue");

        assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(underTest::orElseThrow)
                .withMessage("No success value for an Err.")
                .withNoCause();
    }

    @Test
    public void orElseThrowAppliesThrowableSupplier() {
        final var illegalStateException = new IllegalStateException("Bam!");
        final var value = new Object();
        final var underTest = Err.of(value);

        assertThatThrownBy(() -> underTest.orElseThrow(() -> illegalStateException))
                .isEqualTo(illegalStateException);
    }

    @Test
    public void errReturnsExpectedOptional() {
        final var value = new Object();
        final var underTest = Err.of(value);

        assertThat(underTest.err()).hasValue(value);
    }

    @Test
    public void mapDoesNotApplyMappingFunction() {
        final Function<Object, Object> mappingFunction = Mockito.mock(Function.class);
        final var underTest = Err.of(new Object());

        assertThat(underTest.map(mappingFunction)).isEqualTo(underTest);
        Mockito.verifyNoInteractions(mappingFunction);
    }

    @Test
    public void mapErrWithNullThrowsNullPointerException() {
        final var underTest = Err.of(new Object());

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.mapErr(null))
                .withMessage("mappingFunction")
                .withNoCause();
    }

    @Test
    public void mapErrAppliesMappingFunctionWithErrorValue() {
        final var underTest = Err.of("foo");

        final var mappedResult = underTest.mapErr(value -> value + "bar");

        assertThat(mappedResult.getErrorValue()).isEqualTo("foobar");
    }

    @Test
    public void streamIsEmpty() {
        final var value = new Object();
        final var underTest = Err.of(value);

        assertThat(underTest.stream()).isEmpty();
    }

    @Test
    public void invertReturnsExpectedResult() {
        final var value = new Object();
        final var underTest = Err.of(value);

        assertThat(underTest.invert()).isEqualTo(Result.ok(value));
    }

    @Test
    public void iteratorWorksAsExpected() {
        final var underTest = Err.of(new Object());

        assertThat(underTest).isEmpty();
    }

}