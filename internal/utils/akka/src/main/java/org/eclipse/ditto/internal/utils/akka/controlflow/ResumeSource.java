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

import static akka.stream.Attributes.logLevelDebug;
import static akka.stream.Attributes.logLevelInfo;
import static akka.stream.Attributes.logLevelWarning;
import static akka.stream.Attributes.logLevels;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import akka.NotUsed;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.stream.Attributes;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.OverflowStrategy;
import akka.stream.SourceShape;
import akka.stream.StreamLimitReachedException;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Source;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * From a resumption function and an initial seed, create a source that resumes on error from the last elements that
 * passed through the stream before failure occurred.
 * <pre>
 * {@code
 * seeds<S> +--------------> resumeWithFailuresAppended(resume) +----------> filter +----------------->
 *     ^                                                                       +
 *     |                                                                       |        elements<E>
 *     |                                                                       |
 *     | new seeds                                                      errors |
 *     |                                                                       |
 *     |                         errors                                        |
 *     +                                                                       |
 * feedback: compute new seed <-----------------+ exponential backoff <--------+
 * }
 * </pre>
 */
public final class ResumeSource {

    /**
     * Create a {@code ResumeSourceBuilder} to configure a {@code ResumeSource}.
     *
     * @param <S> type of seeds
     * @param <E> type of elements
     * @return builder for a {@code ResumeSource}.
     */
    public static <S, E> ResumeSourceBuilder<S, E> newBuilder() {
        return new ResumeSourceBuilder<>();
    }

    /**
     * Create a source that resumes based on a seed computed from the final N elements that passed through the stream.
     * Resumption is delayed by exponential backoff with a fixed recovery interval.
     * <ul>
     * <li>Emits when: stream created by {@code resume} emits.</li>
     * <li>Completes when: a stream created by {@code resume} completes.</li>
     * <li>Cancels when: downstream cancels.</li>
     * <li>Fails when: more than {@code maxRestarts} streams created by {@code resume} failed.</li>
     * </ul>
     *
     * @param minBackoff Minimum backoff duration.
     * @param maxBackoff Maximum backoff duration.
     * @param maxRestarts Maximum number of tolerated failures. Tolerate no failure if 0. Tolerate arbitrarily many
     * failures if negative.
     * @param recovery The period after which backoff is reset to {@code minBackoff} if no failure occurred.
     * @param initialSeed The first seed to start the stream.
     * @param resume Creator of streams resuming from a seed.
     * @param lookBehind How many elements to keep in memory to compute the next stream.
     * @param nextSeed Creator of the next seed based on the final elements.
     * @param <S> Type of seeds.
     * @param <E> Type of stream elements.
     * @return A fault-tolerant source.
     */
    public static <S, E> Source<E, NotUsed> onFailureWithBackoff(final Duration minBackoff,
            final Duration maxBackoff,
            final int maxRestarts,
            final Duration recovery,
            final S initialSeed,
            final Function<S, Source<E, ?>> resume,
            final int lookBehind,
            final Function<List<E>, S> nextSeed) {

        return ResumeSource.<S, E>newBuilder()
                .minBackoff(minBackoff)
                .maxBackoff(maxBackoff)
                .maxRestarts(maxRestarts)
                .recovery(recovery)
                .initialSeed(initialSeed)
                .resume(resume)
                .lookBehind(lookBehind)
                .nextSeed(nextSeed)
                .build();
    }

    static <S, E> Source<E, NotUsed> build(final ResumeSourceBuilder<S, E> resumeSourceBuilder) {

        final Duration minBackoff = resumeSourceBuilder.minBackoff;
        final Duration maxBackoff = resumeSourceBuilder.maxBackoff;
        final int maxRestarts = resumeSourceBuilder.maxRestarts;
        final Duration recovery = resumeSourceBuilder.recovery;
        final S initialSeed = resumeSourceBuilder.initialSeed;
        final Function<S, Source<E, ?>> resume = resumeSourceBuilder.resume;
        final int lookBehind = resumeSourceBuilder.lookBehind;
        final Function<List<E>, S> nextSeed = resumeSourceBuilder.nextSeed;
        final Function<Throwable, Optional<Throwable>> mayResumeFrom = resumeSourceBuilder.mapError;

        // upstream of seeds is a feedback loop
        final Flow<S, S, NotUsed> seedsFlow = Flow.<S>create().prepend(Source.single(initialSeed));
        // resume flow from seeds
        final Flow<S, Either<FailureWithLookBehind<E>, E>, NotUsed> resumptionsFlow =
                resumeWithFailuresAppended(resume, lookBehind, mayResumeFrom);
        // pass elements downstream and inject errors into feedback loop
        final Graph<FanOutShape2<Either<FailureWithLookBehind<E>, E>, E, FailureWithLookBehind<E>>, NotUsed>
                filterFanout = Filter.multiplexByEither(Function.identity());
        // backoff exponentially; recover after fixed duration
        final Flow<FailureWithLookBehind<E>, FailureWithLookBehind<E>, NotUsed> backoffFlow =
                backoff(minBackoff, maxBackoff, maxRestarts, recovery);
        // compute new seeds from final elements that passed downstream
        final Flow<FailureWithLookBehind<E>, S, NotUsed> feedbackFlow =
                Flow.<FailureWithLookBehind<E>, List<E>>fromFunction(FailureWithLookBehind::getFinalElements)
                        .map(nextSeed::apply);

        final Graph<SourceShape<E>, NotUsed> graph = GraphDSL.create(builder -> {
            final FlowShape<S, S> seeds = builder.add(seedsFlow);
            final FlowShape<S, Either<FailureWithLookBehind<E>, E>> resumptions = builder.add(resumptionsFlow);
            final FanOutShape2<Either<FailureWithLookBehind<E>, E>, E, FailureWithLookBehind<E>> filter =
                    builder.add(filterFanout);
            final FlowShape<FailureWithLookBehind<E>, FailureWithLookBehind<E>> backoff = builder.add(backoffFlow);
            final FlowShape<FailureWithLookBehind<E>, S> feedback = builder.add(feedbackFlow);

            builder.from(seeds.out()).toInlet(resumptions.in());
            builder.from(resumptions.out()).toInlet(filter.in());
            builder.from(filter.out1()).toInlet(backoff.in());
            builder.from(backoff.out()).toInlet(feedback.in());
            builder.from(feedback.out()).toInlet(seeds.in());

            return SourceShape.of(filter.out0());
        });

        return Source.fromGraph(graph);
    }

    /**
     * Create a flow that delays its elements with exponential backoff and reset after no element passes through the
     * stream for a fixed period of time. Stream elements represent failures and are logged as errors.
     * <ul>
     * <li>Emits when: upstream emits.</li>
     * <li>Completes when: upstream completes.</li>
     * <li>Cancels when: never - this is a part of a feedback loop.</li>
     * <li>Fails when: {@code maxRestarts} elements pass through the stream.</li>
     * </ul>
     *
     * @param minBackoff Minimum backoff duration.
     * @param maxBackoff Maximum backoff duration.
     * @param maxRestarts Maximum number of tolerated failures. Tolerate no failure if 0. Tolerate arbitrarily many
     * failures if negative.
     * @param recovery The period after which backoff is reset to {@code minBackoff} if no failure occurred.
     * @param <E> Type of stream elements.
     * @return A delayed flow.
     */
    private static <E> Flow<E, E, NotUsed> backoff(final Duration minBackoff, final Duration maxBackoff,
            final int maxRestarts, final Duration recovery) {
        final Flow<E, E, NotUsed> neverCancelFlowWithErrorLogging = Flow.<E>create()
                .log("resume-source-errors-flow")
                .withAttributes(logLevels(logLevelInfo(), logLevelDebug(), logLevelWarning()))
                .via(new NeverCancelFlow<>());

        final Flow<E, E, NotUsed> upstream = maxRestarts < 0
                ? neverCancelFlowWithErrorLogging
                : maxRestarts == 0
                ? neverCancelFlowWithErrorLogging.flatMapConcat(ResumeSource::failWithLimitsReached)
                : neverCancelFlowWithErrorLogging.limit(maxRestarts);

        return upstream.statefulMapConcat(() -> new StatefulBackoffFunction<>(minBackoff, maxBackoff, recovery))
                .flatMapConcat(pair ->
                        Source.single(pair.first())
                                .delay(pair.second(), OverflowStrategy.backpressure())
                );
    }

    private static <E> Source<E, NotUsed> failWithLimitsReached(final E element) {
        return Source.failed(new StreamLimitReachedException(0L));
    }

    /**
     * Create a flow from seeds to either elements or failure with reason and the previous N elements that passed
     * through the stream before the failure. For maximum information, even elements emitted 2 or more failures ago
     * are kept in the look-behind queue.
     * <ul>
     * <li>Emits when: stream created by {@code resume} emits or fails.</li>
     * <li>Completes when: a stream created by {@code resume} completes.</li>
     * <li>Cancels when: downstream cancels.</li>
     * <li>Fails when: never.</li>
     * </ul>
     *
     * @param resume Creator of a stream of elements from a resumption seed.
     * @param lookBehind How many elements to keep in memory to create the next seed on failure.
     * @param <S> Type of seeds.
     * @param <E> Type of elements.
     * @return A never-failing flow.
     */
    private static <S, E> Flow<S, Either<FailureWithLookBehind<E>, E>, NotUsed> resumeWithFailuresAppended(
            final Function<S, Source<E, ?>> resume, final int lookBehind,
            final Function<Throwable, Optional<Throwable>> resumeOrMapError) {

        return Flow.<S>create()
                .flatMapConcat(seed -> resume.apply(seed)
                        .<Envelope<E>>map(Element::new)
                        .concat(Source.single(new EndOfStream<>()))
                        .recoverWithRetries(1,
                                new PFBuilder<Throwable, Graph<SourceShape<Envelope<E>>, NotUsed>>()
                                        .matchAny(error -> resumeOrMapError.apply(error)
                                                .<Source<Envelope<E>, NotUsed>>map(Source::failed)
                                                .orElseGet(() -> Source.single(new Error<>(error)))
                                        )
                                        .build())
                )
                .via(new EndStreamOnEOS<>())
                .buffer(1, OverflowStrategy.backpressure())
                .statefulMapConcat(() -> new StatefulLookBehindFunction<>(lookBehind));
    }

    /**
     * Private, non-serializable, local-only message to represent stream element, failure or completion.
     *
     * @param <E> type of elements.
     */
    private interface Envelope<E> {}

    private static final class Element<E> implements Envelope<E> {

        private final E element;

        private Element(final E element) {
            this.element = element;
        }
    }

    private static final class Error<E> implements Envelope<E> {

        private final Throwable error;

        private Error(final Throwable error) {
            this.error = error;
        }
    }

    private static final class EndOfStream<E> implements Envelope<E> {}

    /**
     * A flow that completes downstream and cancels upstream after an EOS-element passes through the stream.
     * <ul>
     * <li>Emits when: upstream emits a non-EOS element.</li>
     * <li>Completes when: upstream completes or emits an EOS element.</li>
     * <li>Cancels when: downstream cancels or upstream emits an EOS element.</li>
     * <li>Fails when: upstream fails.</li>
     * <li>Back-pressures when: downstream back-pressures.</li>
     * </ul>
     *
     * @param <E> Type of elements.
     */
    private static final class EndStreamOnEOS<E> extends GraphStage<FlowShape<Envelope<E>, Envelope<E>>> {

        private final FlowShape<Envelope<E>, Envelope<E>> shape =
                FlowShape.of(Inlet.create("in"), Outlet.create("out"));

        @Override
        public FlowShape<Envelope<E>, Envelope<E>> shape() {
            return shape;
        }

        @Override
        public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
            return new Logic();
        }

        private final class Logic extends GraphStageLogic {

            private Logic() {
                super(shape);

                setHandler(shape.in(), new AbstractInHandler() {
                    @Override
                    public void onPush() {
                        final Envelope<E> envelope = grab(shape.in());
                        if (envelope instanceof EndOfStream) {
                            // stream ended
                            cancel(shape.in());
                            complete(shape.out());
                        } else {
                            push(shape.out(), envelope);
                        }
                    }
                });

                setHandler(shape.out(), new AbstractOutHandler() {
                    @Override
                    public void onPull() {
                        pull(shape.in());
                    }
                });
            }
        }
    }

    /**
     * A flow that never cancels. It is used to force termination order in a feedback loop.
     * <ul>
     * <li>Emits when: upstream emits.</li>
     * <li>Completes when: upstream completes.</li>
     * <li>Cancels when: never.</li>
     * <li>Fails when: upstream fails.</li>
     * <li>Back-pressures when: downstream back-pressures or anytime after downstream termination.</li>
     * </ul>
     *
     * @param <E> Type of elements.
     */
    private static final class NeverCancelFlow<E> extends GraphStage<FlowShape<E, E>> {

        private final FlowShape<E, E> shape =
                FlowShape.of(Inlet.create("in"), Outlet.create("out"));

        @Override
        public FlowShape<E, E> shape() {
            return shape;
        }

        @Override
        public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
            return new Logic();
        }

        private final class Logic extends GraphStageLogic {

            private Logic() {
                super(shape);

                setHandler(shape.in(), new AbstractInHandler() {
                    @Override
                    public void onPush() {
                        push(shape.out(), grab(shape.in()));
                    }
                });

                setHandler(shape.out(), new AbstractOutHandler() {
                    @Override
                    public void onPull() {
                        pull(shape.in());
                    }

                    @Override
                    public void onDownstreamFinish() {
                        // become a stuck stream that infinitely back-pressures.
                    }
                });
            }
        }
    }

    /**
     * Private, non-serializable, local-only message for {@code ResumeSource} containing a look-behind element queue and
     * the error that caused a stream failure.
     *
     * @param <E> Type of elements.
     */
    private static final class FailureWithLookBehind<E> {

        private final List<E> finalElements;
        private final Throwable error;

        private FailureWithLookBehind(final List<E> finalElements, final Throwable error) {
            this.finalElements = finalElements;
            this.error = error;
        }

        private List<E> getFinalElements() {
            return finalElements;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[error=" + error + ", finalElements=" + finalElements + "]";
        }
    }

    /**
     * Stateful function to exponential backoff for a stream of errors.
     *
     * @param <E> type of errors.
     */
    private static final class StatefulBackoffFunction<E>
            implements akka.japi.function.Function<E, Iterable<Pair<E, Duration>>> {

        private final Duration minBackoff;
        private final Duration maxBackoff;
        private final Duration recovery;

        private Instant lastError;
        private Duration lastBackoff;

        private StatefulBackoffFunction(final Duration minBackoff, final Duration maxBackoff, final Duration recovery) {
            this.minBackoff = minBackoff;
            this.maxBackoff = maxBackoff;
            this.recovery = recovery;

            lastError = Instant.EPOCH;
            lastBackoff = minBackoff;
        }

        @Override
        public Iterable<Pair<E, Duration>> apply(final E element) {

            final Instant thisError = Instant.now();
            final Duration thisBackoff;

            if (lastError.plus(recovery).isBefore(thisError)) {
                // recovery period passed with no error; reset backoff to minimum.
                thisBackoff = minBackoff;
            } else {
                final Duration nextBackoff = lastBackoff.multipliedBy(2L);
                thisBackoff = maxBackoff.minus(nextBackoff).isNegative() ? maxBackoff : nextBackoff;
            }

            lastError = thisError;
            lastBackoff = thisBackoff;
            return Collections.singletonList(Pair.create(element, thisBackoff));
        }
    }

    /**
     * Stateful function to collect the final N non-error elements that pass through a stream and emit them in a list
     * on each error.
     *
     * @param <E> Type of elements.
     */
    private static final class StatefulLookBehindFunction<E> implements
            akka.japi.function.Function<Envelope<E>, Iterable<Either<FailureWithLookBehind<E>, E>>> {

        private final int lookBehind;
        private final ArrayDeque<E> finalElements;

        private StatefulLookBehindFunction(final int lookBehind) {
            this.lookBehind = lookBehind;
            finalElements = new ArrayDeque<>(lookBehind + 1);
        }

        @Override
        public Iterable<Either<FailureWithLookBehind<E>, E>> apply(final Envelope<E> param) {
            if (param instanceof Element) {
                final E element = ((Element<E>) param).element;
                enqueue(element);
                return Collections.singletonList(new Right<>(element));
            } else if (param instanceof Error) {
                final List<E> finalElementsCopy = new ArrayList<>(finalElements);
                final Throwable error = ((Error<E>) param).error;
                return Collections.singletonList(new Left<>(new FailureWithLookBehind<>(finalElementsCopy, error)));
            } else {
                // param is EOS; this is not supposed to happen.
                throw new IllegalStateException("End-of-stream encountered; stream should be complete already!");
            }
        }

        private void enqueue(final E element) {
            finalElements.addLast(element);
            while (finalElements.size() > lookBehind) {
                finalElements.removeFirst();
            }
        }
    }
}
