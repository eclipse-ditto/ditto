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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Ok}.
 */
public final class OkTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(Ok.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> Ok.of(null))
                .withMessage("value")
                .withNoCause();
    }

    @Test
    public void getSuccessValueReturnsExpectedValue() {
        final var value = new Object();
        final var underTest = Ok.of(value);

        assertThat(underTest.getSuccessValue()).isEqualTo(value);
    }

    @Test
    public void isOkReturnsTrue() {
        final var underTest = Ok.of(new Object());

        assertThat(underTest.isOk()).isTrue();
    }

    @Test
    public void isErrReturnsFalse() {
        final var underTest = Ok.of(new Object());

        assertThat(underTest.isErr()).isFalse();
    }

    @Test
    public void ifOkWithNullThrowsNullPointerException() {
        final var underTest = Ok.of(new Object());

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.ifOk(null))
                .withMessage("successValueAction")
                .withNoCause();
    }

    @Test
    public void ifOkAppliesSpecifiedSuccessValueAction() {
        final var value = new Object();
        final Consumer<Object> successValueAction = Mockito.mock(Consumer.class);
        final var underTest = Ok.of(value);

        underTest.ifOk(successValueAction);

        Mockito.verify(successValueAction).accept(Mockito.eq(value));
    }

    @Test
    public void ifErrDoesNotApplyErrorValueAction() {
        final var value = new Object();
        final Consumer<Object> errorValueAction = Mockito.mock(Consumer.class);
        final var underTest = Ok.of(value);

        underTest.ifErr(errorValueAction);

        Mockito.verifyNoInteractions(errorValueAction);
    }

    @Test
    public void okReturnsExpectedOptional() {
        final var value = new Object();
        final var underTest = Ok.of(value);

        assertThat(underTest.ok()).hasValue(value);
    }

    @Test
    public void orElseReturnsOriginalValue() {
        final var value = new Object();
        final var underTest = Ok.of(value);

        assertThat(underTest.orElse(new Object())).isEqualTo(value);
    }

    @Test
    public void orElseGetReturnsOriginalValue() {
        final var value = new Object();
        final var underTest = Ok.of(value);

        assertThat(underTest.orElseGet(Object::new)).isEqualTo(value);
    }

    @Test
    public void orElseThrowReturnsOriginalValue() {
        final var value = new Object();
        final var underTest = Ok.of(value);

        assertThat(underTest.orElseThrow()).isEqualTo(value);
    }

    @Test
    public void orElseThrowReturnsOriginalValueWithApplyingThrowableSupplier() {
        final Supplier<IllegalStateException> throwableSupplier = Mockito.mock(Supplier.class);
        final var value = new Object();
        final var underTest = Ok.of(value);

        assertThat(underTest.orElseThrow(throwableSupplier)).isEqualTo(value);
        Mockito.verifyNoInteractions(throwableSupplier);
    }

    @Test
    public void errReturnsEmptyOptional() {
        final var underTest = Ok.of(new Object());

        assertThat(underTest.err()).isEmpty();
    }

    @Test
    public void mapWithNullThrowsNullPointerException() {
        final var underTest = Ok.of(new Object());

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.map(null))
                .withMessage("mappingFunction")
                .withNoCause();
    }

    @Test
    public void mapAppliesMappingFunctionWithSuccessValue() {
        final var underTest = Ok.of("foo");

        final var mappedResult = underTest.map(value -> value + "bar");

        assertThat(mappedResult.getSuccessValue()).isEqualTo("foobar");
    }

    @Test
    public void mapErrDoesNotApplyMappingFunction() {
        final var underTest = Ok.of("foo");

        final var mappedResult = underTest.mapErr(value -> value + "bar");

        assertThat(mappedResult).isEqualTo(underTest);
    }

    @Test
    public void streamContainsSuccessValue() {
        final var value = new Object();
        final var underTest = Ok.of(value);

        assertThat(underTest.stream()).containsOnly(value);
    }

    @Test
    public void invertReturnsExpectedResult() {
        final var value = new Object();
        final var underTest = Ok.of(value);

        assertThat(underTest.invert()).isEqualTo(Result.err(value));
    }

    @Test
    public void iteratorWorksAsExpected() {
        final var value = new Object();
        final var underTest = Ok.of(value);

        assertThat(underTest).containsOnly(value);
    }

}