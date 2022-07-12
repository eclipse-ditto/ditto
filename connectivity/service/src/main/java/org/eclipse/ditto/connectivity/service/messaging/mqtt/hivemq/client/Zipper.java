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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Utility class for zipping the elements two iterables into a stream of {@link Zipped} elements.
 */
@Immutable
// Could be easily modified to accept Streams as well.
final class Zipper {

    private Zipper() {
        throw new AssertionError();
    }

    /**
     * Zips the elements of the two specified {@code Iterable} arguments.
     * Zipping means that the elements at the same index of both Iterables are fused yielding a {@link Zipped}.
     * The returned Stream has the same length as the Iterable argument with the fewest elements.
     *
     * @param <A> type of the elements of {@code iterableA}.
     * @param <B> type of the elements of {@code iterableB}.
     * @param iterableA the first Iterable to be zipped.
     * @param iterableB the second Iterable to be zipped.
     * @return a Stream of {@code Zipped}s. Each Zipped contains the correlated elements of {@code iterableA} and
     * {@code iterableB}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static <A, B> Stream<Zipped<A, B>> zipIterables(final Iterable<A> iterableA, final Iterable<B> iterableB) {
        ConditionChecker.checkNotNull(iterableA, "iterableA");
        ConditionChecker.checkNotNull(iterableB, "iterableB");
        final var iteratorA = iterableA.iterator();
        final var iteratorB = iterableB.iterator();
        final var zipIterator = new Iterator<Zipped<A, B>>() {
            @Override
            public boolean hasNext() {
                return iteratorA.hasNext() && iteratorB.hasNext();
            }

            @Override
            public Zipped<A, B> next() {
                return new Zipped<>(iteratorA.next(), iteratorB.next());
            }
        };

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(zipIterator, Spliterator.ORDERED), false);
    }

}

