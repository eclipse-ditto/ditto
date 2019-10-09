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

import java.time.Duration;
import java.util.Collections;
import java.util.function.Function;

import akka.NotUsed;
import akka.japi.function.Creator;
import akka.stream.FanOutShape2;
import akka.stream.Graph;
import akka.stream.javadsl.Flow;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * A stream processor forwarding or rejecting elements based on their position in a time interval.
 * <pre>
 * {@code
 *                         +----------------------+
 *                         |                      |  first k elements in a time interval
 *  input +--------------->+ LimitRateByRejection +--------------------------> output
 *                         |                      |
 *                         +-------+--------------+
 *                                 |
 *                                 | (k + 1)th element onward
 *                                 | in a time interval
 *                                 |
 *                                 v
 *                               error
 * }
 * </pre>
 */
public final class LimitRateByRejection {

    private LimitRateByRejection() {
        throw new AssertionError();
    }

    /**
     * Multiplex messages by an Either mapper.
     *
     * @param <A> type of elements.
     * @param <E> type of errors.
     * @param timeWindow size of each time window.
     * @param maxElements number of elements to let through in each time window.
     * @param errorReporter creator of error from each rejected element.
     * @return graph with 1 inlet for messages and 2 outlets, the first outlet for messages mapped to the right
     * and the second outlet for messages mapped to the left.
     */
    public static <A, E> Graph<FanOutShape2<A, A, E>, NotUsed> of(
            final Duration timeWindow,
            final int maxElements,
            final Function<A, E> errorReporter) {

        return Filter.multiplexByEitherFlow(Flow.<A>create()
                .statefulMapConcat(Logic.creator(timeWindow, maxElements, errorReporter))
        );
    }

    private static final class Logic<A, E> implements akka.japi.function.Function<A, Iterable<Either<E, A>>> {

        private final long windowSize;
        private final int maxElements;
        private final Function<A, E> errorReporter;

        private long previousWindow = 0L;
        private int counter = 0;

        private Logic(final long windowSize, final int maxElements,
                final Function<A, E> errorReporter) {
            this.windowSize = windowSize;
            this.maxElements = maxElements;
            this.errorReporter = errorReporter;
        }

        private static <A, E> Creator<akka.japi.function.Function<A, Iterable<Either<E, A>>>> creator(
                final Duration timeWindow, final int maxElements, final Function<A, E> errorReporter) {

            final long timeWindowMillis = timeWindow.toMillis();
            return () -> new Logic<>(timeWindowMillis, maxElements, errorReporter);
        }

        @Override
        public Iterable<Either<E, A>> apply(final A element) {
            final long currentTime = System.currentTimeMillis();
            if (currentTime - previousWindow >= windowSize) {
                previousWindow = currentTime;
                counter = 1;
            } else {
                counter++;
            }
            final Either<E, A> result;
            if (counter <= maxElements) {
                result = Right.apply(element);
            } else {
                result = Left.apply(errorReporter.apply(element));
            }
            return Collections.singletonList(result);
        }
    }
}
