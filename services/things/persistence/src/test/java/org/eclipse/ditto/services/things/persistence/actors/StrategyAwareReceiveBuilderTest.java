/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.services.things.persistence.actors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.function.Consumer;

import org.eclipse.ditto.services.things.persistence.strategies.ReceiveStrategy;
import org.junit.Before;
import org.junit.Test;

import akka.actor.AbstractActor;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Tests {@link StrategyAwareReceiveBuilder}.
 */
@SuppressWarnings("unchecked")
public class StrategyAwareReceiveBuilderTest {

    private final DiagnosticLoggingAdapter log = mock(DiagnosticLoggingAdapter.class);
    private StrategyAwareReceiveBuilder underTest;
    private ReceiveStrategy doubleReceiveStrategy;
    private ReceiveStrategy longReceiveStrategy;
    private ReceiveStrategy anyReceiveStrategy;

    @Before
    public void init() {
        underTest = new StrategyAwareReceiveBuilder(log);
        doubleReceiveStrategy = mockStrategy(Double.class);
        longReceiveStrategy = mockStrategy(Long.class);
        anyReceiveStrategy = mockStrategy(Object.class);
    }

    @Test
    public void testMatchAny() {
        final AbstractActor.Receive receive = underTest
                .match(doubleReceiveStrategy)
                .match(longReceiveStrategy)
                .matchAny(anyReceiveStrategy)
                .build();

        receive.onMessage().apply(1D);
        receive.onMessage().apply(1L);
        receive.onMessage().apply(1);

        verify(doubleReceiveStrategy).apply(1D);
        verify(longReceiveStrategy).apply(1L);
        verify(anyReceiveStrategy).apply(1);
    }

    @Test
    public void testMatchEachWithPeekConsumer() {
        final Consumer peekConsumer = mock(Consumer.class);

        final AbstractActor.Receive receive = underTest
                .matchEach(Arrays.asList(doubleReceiveStrategy, longReceiveStrategy))
                .matchAny(anyReceiveStrategy)
                .setPeekConsumer(peekConsumer)
                .build();

        receive.onMessage().apply(1D);
        receive.onMessage().apply(1L);
        receive.onMessage().apply(1);

        verify(doubleReceiveStrategy).apply(1D);
        verify(longReceiveStrategy).apply(1L);
        verify(anyReceiveStrategy).apply(1);
        verify(peekConsumer).accept(1D);
        verify(peekConsumer).accept(1L);
        verify(peekConsumer).accept(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddSameClassTwiceThrowsException() {
        final ReceiveStrategy anyReceiveStrategy = mockStrategy(Object.class);
        underTest.match(anyReceiveStrategy).match(anyReceiveStrategy).build();
    }

    @Test(expected = NullPointerException.class)
    public void testAddNullMatchAnyThrowsNPE() {
        underTest.matchAny(null).build();
    }

    @Test(expected = NullPointerException.class)
    public void testAddNullMatchEachThrowsNPE() {
        underTest.matchEach(null).build();
    }

    private <T> ReceiveStrategy<T> mockStrategy(final Class<T> clz) {
        final ReceiveStrategy<T> receiveStrategy = (ReceiveStrategy<T>) mock(ReceiveStrategy.class);
        when(receiveStrategy.getMatchingClass()).thenReturn(clz);
        return receiveStrategy;
    }

}