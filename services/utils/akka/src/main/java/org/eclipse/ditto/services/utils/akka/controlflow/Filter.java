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
package org.eclipse.ditto.services.utils.akka.controlflow;

import java.util.function.Predicate;

import akka.stream.Attributes;
import akka.stream.FanOutShape2;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

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
 *
 * @param <T> type of messages to filter for.
 */
public final class Filter<T> extends GraphStage<FanOutShape2<WithSender, WithSender<T>, WithSender>> {

    private final FanOutShape2<WithSender, WithSender<T>, WithSender> shape =
            new FanOutShape2<>(Inlet.create("input"), Outlet.create("output"), Outlet.create("unhandled"));

    private final Class<T> clazz;
    private final Predicate<T> predicate;

    private Filter(final Class<T> clazz, final Predicate<T> predicate) {
        this.clazz = clazz;
        this.predicate = predicate;
    }

    /**
     * Create a filter stage from a class and a predicate.
     *
     * @param <T> type of messages to filter for.
     * @param clazz class of {@code T}.
     * @param predicate predicate to test instances of {@code T} with.
     * @return {@code GraphStage} that performs the filtering.
     */
    public static <T> Filter<T> of(final Class<T> clazz, final Predicate<T> predicate) {
        return new Filter<>(clazz, predicate);
    }


    /**
     * Create a filter stage from a class.
     *
     * @param <T> type of messages to filter for.
     * @param clazz class of {@code T}.
     * @return {@code GraphStage} that performs the filtering.
     */
    public static <T> Filter<T> of(final Class<T> clazz) {
        return of(clazz, x -> true);
    }

    @Override
    public FanOutShape2<WithSender, WithSender<T>, WithSender> shape() {
        return shape;
    }

    @Override
    public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
        return new AbstractControlFlowLogic(shape) {
            {
                initOutlets(shape);

                when(shape.in(), wrapped -> {
                    if (clazz.isInstance(wrapped.getMessage())) {
                        final T message = clazz.cast(wrapped.getMessage());
                        if (predicate.test(message)) {
                            emit(shape.out0(), WithSender.of(message, wrapped.getSender()));
                        } else {
                            emit(shape.out1(), wrapped);
                        }
                    } else {
                        emit(shape.out1(), wrapped);
                    }
                });
            }
        };
    }
}
