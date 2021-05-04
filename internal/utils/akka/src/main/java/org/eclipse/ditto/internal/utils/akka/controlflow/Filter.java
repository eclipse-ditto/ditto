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
package org.eclipse.ditto.internal.utils.akka.controlflow;

import java.util.Optional;
import java.util.function.Function;

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
     * Multiplex messages by an optional mapper.
     *
     * @param mapper partial mapper of messages.
     * @param <A> type of all messages.
     * @param <B> type of accepted messages.
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
     * @param <A> type of all messages.
     * @param <B> type of accepted messages.
     * @param <C> type of rejected messages.
     * @return graph with 1 inlet for messages and 2 outlets, the first outlet for messages mapped to the right
     * and the second outlet for messages mapped to the left.
     */
    public static <A, B, C> Graph<FanOutShape2<A, B, C>, NotUsed> multiplexByEither(
            final Function<A, Either<C, B>> mapper) {
        return multiplexByEitherFlow(Flow.fromFunction(mapper::apply));
    }

    /**
     * Multiplex messages by an Either flow.
     *
     * @param eitherFlow the filter implemented as a flow from messages to Either of accepted or rejected messages.
     * @param <A> type of all messages.
     * @param <B> type of accepted messages.
     * @param <C> type of rejected messages.
     * @return graph with 1 inlet for messages and 2 outlets, the first outlet for messages mapped to the right
     * and the second outlet for messages mapped to the left.
     */
    public static <A, B, C> Graph<FanOutShape2<A, B, C>, NotUsed> multiplexByEitherFlow(
            final Graph<FlowShape<A, Either<C, B>>, ?> eitherFlow) {

        return GraphDSL.create(builder -> {
            final FlowShape<A, Either<C, B>> testPredicate =
                    builder.add(eitherFlow);

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
