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
     * Creates a graph limiting the inlet elements to be not more than the configured {@code maxElements} in the
     * passed {@code timeWindow}. All additionally flowing through elements, surpassing the max elements per time frame,
     * are passed to the {@code errorReporter} function which builds the error passed to the second outlet instead of
     * reaching the first outlet.
     *
     * @param <A> type of elements.
     * @param <E> type of errors.
     * @param timeWindow size of each time window. A windows {@code <= 0} disables the rate limiting.
     * @param maxElements number of elements to let through in each time window - a value of {@code <= 0} disables the
     * rate limiting.
     * @param errorReporter creator of error from each rejected element.
     * @return graph with 1 inlet for messages and 2 outlets, the first outlet for messages which were in the configured
     * limits per time window and the second outlet for error messages which surpassed the limit.
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

        private final long windowSizeMillis;
        private final int maxElements;
        private final transient Function<A, E> errorReporter;
        private final boolean enabled;

        private long previousWindow = 0L;
        private int counter = 0;

        private Logic(final long windowSizeMillis, final int maxElements,
                final Function<A, E> errorReporter) {
            this.windowSizeMillis = windowSizeMillis;
            this.maxElements = maxElements;
            this.errorReporter = errorReporter;
            enabled = maxElements > 0 && windowSizeMillis > 0;
        }

        private static <A, E> Creator<akka.japi.function.Function<A, Iterable<Either<E, A>>>> creator(
                final Duration timeWindow, final int maxElements, final Function<A, E> errorReporter) {

            final long timeWindowMillis = timeWindow.toMillis();
            return () -> new Logic<>(timeWindowMillis, maxElements, errorReporter);
        }

        @Override
        public Iterable<Either<E, A>> apply(final A element) {
            if (enabled) {
                final long currentTime = System.currentTimeMillis();
                if (currentTime - previousWindow >= windowSizeMillis) {
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
            } else {
                return Collections.singletonList(Right.apply(element));
            }
        }
    }
}
