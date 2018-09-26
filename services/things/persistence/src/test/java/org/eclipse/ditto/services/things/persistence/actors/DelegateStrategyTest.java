/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.services.things.persistence.actors;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.services.things.persistence.strategies.DelegateStrategy;
import org.eclipse.ditto.services.things.persistence.strategies.ReceiveStrategy;
import org.eclipse.ditto.services.things.persistence.strategies.ReceiveStrategy.WithDefined;
import org.eclipse.ditto.services.things.persistence.strategies.ReceiveStrategy.WithUnhandledFunction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Tests {@link org.eclipse.ditto.services.things.persistence.strategies.DelegateStrategy}.
 */
@SuppressWarnings("unchecked")
public class DelegateStrategyTest {

    private static final DiagnosticLoggingAdapter log = mock(DiagnosticLoggingAdapter.class);
    private ReceiveStrategy<Double> doubleStrategy;
    private ReceiveStrategy<Integer> intStrategy;
    private WithDefined<Integer> intStrategyWithDefined;
    private WithUnhandledFunction<Integer> intStrategyWithUnhandled;

    public DelegateStrategyTest() {}

    @Before
    public void init() {
        doubleStrategy = mockStrategy(Double.class);
        intStrategy = mockStrategy(Integer.class);
        intStrategyWithDefined = mockWithDefined(Integer.class);
        intStrategyWithUnhandled = mockWithUnhandled(Integer.class);

        // Int Strategy is only defined for values >= 0
        final Answer<Object> definedAnswer = invocation -> ((Integer) invocation.getArgument(0)) >= 0;
        when(intStrategyWithDefined.isDefined(anyInt())).thenAnswer(definedAnswer);
        when(intStrategyWithUnhandled.isDefined(anyInt())).thenAnswer(definedAnswer);
    }

    @Test
    public void isDefined() {
        final DelegateStrategy underTest = initDelegateStrategy(doubleStrategy, intStrategy);

        Assertions.assertThat(underTest.isDefined(1D)).isTrue();
        Assertions.assertThat(underTest.isDefined(1)).isTrue();
        Assertions.assertThat(underTest.isDefined(1L)).isFalse();
        Assertions.assertThat(underTest.isDefined(true)).isFalse();
        Assertions.assertThat(underTest.isDefined(new Object())).isFalse();
    }

    @Test
    public void applyWithoutDefined() {
        final DelegateStrategy underTest = initDelegateStrategy(doubleStrategy, intStrategy);
        underTest.apply(1);
        verify(intStrategy).apply(1);
    }

    @Test
    public void applyWithDefined() {
        final DelegateStrategy underTest = initDelegateStrategy(doubleStrategy, intStrategyWithDefined);
        underTest.apply(1);
        underTest.apply(-1);
        verify(intStrategyWithDefined).apply(1);
        verify(intStrategyWithDefined, never()).apply(-1);
    }

    @Test
    public void applyWithUnhandledIfDefined() {
        final DelegateStrategy underTest = initDelegateStrategy(doubleStrategy, intStrategyWithUnhandled);
        underTest.apply(1);
        verify(intStrategyWithUnhandled).apply(1);
        verify(intStrategyWithUnhandled, never()).unhandled(1);

        underTest.apply(-1);
        verify(intStrategyWithUnhandled, never()).apply(-1);
        verify(intStrategyWithUnhandled).unhandled(-1);
    }

    private static DelegateStrategy initDelegateStrategy(final ReceiveStrategy<?>... strategies) {
        final Map<Class<?>, ReceiveStrategy> mapping = new HashMap<>();
        Stream.of(strategies).forEach(s -> mapping.put(s.getMatchingClass(), s));
        return new DelegateStrategy(mapping, log);
    }

    private static <T> ReceiveStrategy<T> mockStrategy(final Class<T> clz) {
        final ReceiveStrategy<T> receiveStrategy = (ReceiveStrategy<T>) mock(ReceiveStrategy.class);
        when(receiveStrategy.getMatchingClass()).thenReturn(clz);
        return receiveStrategy;
    }

    private static <T> WithDefined<T> mockWithDefined(final Class<T> clz) {
        final WithDefined<T> receiveStrategy = (WithDefined<T>) mock(WithDefined.class);
        when(receiveStrategy.getMatchingClass()).thenReturn(clz);
        return receiveStrategy;
    }

    private static <T> WithUnhandledFunction<T> mockWithUnhandled(final Class<T> clz) {
        final WithUnhandledFunction<T> receiveStrategy = (WithUnhandledFunction<T>) mock(WithUnhandledFunction.class);
        when(receiveStrategy.getMatchingClass()).thenReturn(clz);
        return receiveStrategy;
    }
}
