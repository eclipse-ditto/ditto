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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Utility class for zipping two Lists.
 */
@Immutable
// Could be easily modified to accept Iterables instead of List.
// Even a second method that accepts Streams as arguments would be easily possible.
final class ListZipper {

    private ListZipper() {
        throw new AssertionError();
    }

    /**
     * Zips the two specified {@code List} arguments.
     * Zipping means that the elements at the same index of both Lists are fused yielding a {@link Zipped}.
     * The returned Stream has the same length as the List argument with the fewest elements.
     *
     * @param listA the first List to be zipped.
     * @param listB the second List to be zipped.
     * @param <A> type of the elements of {@code listA}.
     * @param <B> type of the elements of {@code listB}.
     * @return a Stream of {@code Zipped}s. Each Zipped contains the correlated elements of {@code listA} and
     * {@code listB}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static <A, B> Stream<Zipped<A, B>> zipLists(final List<A> listA, final List<B> listB) {
        ConditionChecker.checkNotNull(listA, "listA");
        ConditionChecker.checkNotNull(listB, "listB");
        final var iteratorA = listA.iterator();
        final var iteratorB = listB.iterator();
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

