/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.akka.controlflow;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.UniformFanOutShape;
import akka.stream.javadsl.Broadcast;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;

/**
 * A stream processor filtering messages by type and by a predicate.
 * <pre>
 * {@code
 *                         +-----------------+
 *                         |                 |  is instance of T and
 *                         | Filter<T>       |  passes predicate check
 *  input +--------------->+ with predicate  +--------------------------> output
 *                         |                 |
 *                         +-------+---------+
 *                                 |
 *                                 |
 *                                 | is no instance of T or
 *                                 | fails predicate check
 *                                 |
 *                                 v
 *                              unhandled
 * }
 * </pre>
 */
public final class Filter {

    private Filter() {
        throw new AssertionError();
    }

    /**
     * Create a filter stage from a class and a predicate.
     *
     * @param <T> type of messages to filter for.
     * @param clazz class of {@code T}.
     * @param predicate predicate to test instances of {@code T} with.
     * @return {@code GraphStage} that performs the filtering.
     */
    public static <T extends WithDittoHeaders> Graph<FanOutShape2<WithSender, WithSender<T>, WithSender>, NotUsed> of(
            final Class<T> clazz,
            final Predicate<T> predicate) {

        return Filter.multiplexBy(withSender -> {
            // introduce wildcard type parameter to un-confuse type-checker
            final WithSender<?> input = (WithSender<?>) withSender;
            if (clazz.isInstance(input.getMessage())) {
                final T message = clazz.cast(input.getMessage());
                if (predicate.test(message)) {
                    return Optional.of(input.withMessage(message));
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Create a filter stage from a class.
     *
     * @param <T> type of messages to filter for.
     * @param clazz class of {@code T}.
     * @return {@code GraphStage} that performs the filtering.
     */
    public static <T extends WithDittoHeaders> Graph<FanOutShape2<WithSender, WithSender<T>, WithSender>, NotUsed> of(
            final Class<T> clazz) {
        return of(clazz, x -> true);
    }

    /**
     * Multiplex messages by an optional mapper.
     *
     * @param mapper partial mapper of messages.
     * @param <A> type of messages.
     * @return graph with 1 inlet for messages and 2 outlets, the first outlet for messages mapped successfully
     * and the second outlet for other messages.
     */
    public static <A, B> Graph<FanOutShape2<A, B, A>, NotUsed> multiplexBy(final Function<A, Optional<B>> mapper) {

        return GraphDSL.create(builder -> {
            final FlowShape<A, Pair<A, Optional<B>>> testPredicate =
                    builder.add(Flow.fromFunction(x -> Pair.create(x, mapper.apply(x))));

            final UniformFanOutShape<Pair<A, Optional<B>>, Pair<A, Optional<B>>> broadcast =
                    builder.add(Broadcast.create(2));

            final FlowShape<Pair<A, Optional<B>>, B> filterTrue =
                    builder.add(Flow.<Pair<A, Optional<B>>, Optional<B>>fromFunction(Pair::second)
                            .filter(Optional::isPresent)
                            .map(Optional::get));

            final FlowShape<Pair<A, Optional<B>>, A> filterFalse =
                    builder.add(Flow.<Pair<A, Optional<B>>>create()
                            .filter(pair -> !pair.second().isPresent())
                            .map(Pair::first));

            builder.from(testPredicate.out()).toInlet(broadcast.in());
            builder.from(broadcast.out(0)).toInlet(filterTrue.in());
            builder.from(broadcast.out(1)).toInlet(filterFalse.in());
            return new FanOutShape2<>(testPredicate.in(), filterTrue.out(), filterFalse.out());
        });
    }

}
