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
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.UniformFanOutShape;
import akka.stream.javadsl.Broadcast;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

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
        return multiplexByEither(a -> mapper.apply(a).<Either<A, B>>map(Right::new).orElseGet(() -> new Left<>(a)));
    }

    /**
     * Multiplex messages by an Either mapper.
     *
     * @param mapper mapper of messages.
     * @param <A> type of messages.
     * @return graph with 1 inlet for messages and 2 outlets, the first outlet for messages mapped to the right
     * and the second outlet for messages mapped to the left.
     */
    public static <A, B, C> Graph<FanOutShape2<A, B, C>, NotUsed> multiplexByEither(
            final Function<A, Either<C, B>> mapper) {

        return GraphDSL.create(builder -> {
            final FlowShape<A, Either<C, B>> testPredicate =
                    builder.add(Flow.fromFunction(mapper::apply));

            final UniformFanOutShape<Either<C, B>, Either<C, B>> broadcast =
                    builder.add(Broadcast.create(2, true));

            final FlowShape<Either<C, B>, B> filterTrue =
                    builder.add(Flow.<Either<C, B>>create()
                            .filter(Either::isRight)
                            .map(either -> either.right().get()));

            final FlowShape<Either<C, B>, C> filterFalse =
                    builder.add(Flow.<Either<C, B>>create()
                            .filter(Either::isLeft)
                            .map(either -> either.left().get()));

            builder.from(testPredicate.out()).toInlet(broadcast.in());
            builder.from(broadcast.out(0)).toInlet(filterTrue.in());
            builder.from(broadcast.out(1)).toInlet(filterFalse.in());
            return new FanOutShape2<>(testPredicate.in(), filterTrue.out(), filterFalse.out());
        });
    }

}
