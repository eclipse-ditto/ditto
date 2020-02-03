/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.util.Comparator;

import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.FanInShape2;
import akka.stream.Graph;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

/**
 * Performs merge-sort on 2 streams but instead of delivering the sort result, delivers the tuple before
 * the final comparison.
 * <p>
 * Behavior definition:
 * <pre>{@code
 * akka.stream.scaladsl.MergeSorted
 * }</pre>
 * is behaviorally equivalent as stream component with
 * <pre>{@code
 * MergeSortAsPair ~> Flow.create().flatMapConcat(pair -> {
 *                        if (pair.first() < pair.second()) {
 *                            return Source.single(pair.first());
 *                        } else if (pair.first() > pair.second()) {
 *                            return Source.single(pair.second());
 *                        } else {
 *                            // pair.first() == pair.second()
 *                            return Source.from(List.of(pair.first(), pair.second()));
 *                        }
 *                    })
 * }</pre>
 * <p>
 * The first component of each pair element is always an element of the first stream; the second component
 * is always an element of the second stream. If one stream completes before the other, then it is padded
 * by a user-supplied maximal element.
 * <p>
 * Example in pseudo-Scala:
 * <pre>{@code
 * // GIVEN
 * source1 = Source.from(List.of(1, 3, 5, 7, 9))
 * source2 = Source.from(List.of(2, 3, 4, 5, 6))
 *
 * // WHEN
 * source3 = GraphDSL.create() { implicit builder =>
 *   val mergeSortAsPair = builder.add(MergeSortAsPair.getInstance(Integer.MAX_VALUE))
 *   builder.add(source1) ~> mergeSortAsPair
 *   builder.add(source2) ~> mergeSortAsPair
 *   ClosedShape
 * }
 *
 * // THEN
 * source3 == Source.from(List.of(
 *   Pair.create(1, 2),
 *   Pair.create(3, 2),
 *   Pair.create(3, 3),
 *   Pair.create(5, 4),
 *   Pair.create(5, 5),
 *   Pair.create(7, 6),
 *   Pair.create(7, Integer.MAX_VALUE)
 *   Pair.create(9, Integer.MAX_VALUE)
 * ))
 * }</pre>
 */
public final class MergeSortedAsPair<T> extends GraphStage<FanInShape2<T, T, Pair<T, T>>> {

    private final Inlet<T> in1 = Inlet.create("in1");
    private final Inlet<T> in2 = Inlet.create("in2");
    private final Outlet<Pair<T, T>> out = Outlet.create("out");
    private final FanInShape2<T, T, Pair<T, T>> shape = new FanInShape2<>(in1, in2, out);

    private final Comparator<T> comparator;
    private final T maximalElement;

    private MergeSortedAsPair(final T maximalElement, final Comparator<T> comparator) {
        this.comparator = comparator;
        this.maximalElement = maximalElement;
    }

    /**
     * Create a {@code MergeSortedAsPair} stage for a comparable type from a maximal element.
     * An element {@code e} is maximal if {@code x.compareTo(e) <= 0} for all instances {@code x} of the element type.
     *
     * @param maximalElement a maximal element of the comparable type.
     * @param <T> the comparable type of elements.
     * @return the {@code MergeSortedAsPair} stage.
     */
    public static <T extends Comparable<T>> Graph<FanInShape2<T, T, Pair<T, T>>, NotUsed> getInstance(
            final T maximalElement) {

        return getInstance(maximalElement, Comparable::compareTo);
    }

    /**
     * Create a {@code MergeSortedAsPair} stage from a comparator and a maximal element of it.
     * An element {@code e} is maximal if {@code x.compareTo(e) <= 0} for all instances {@code x} of the element type.
     *
     * @param maximalElement a maximal element according to the comparator.
     * @param comparator the comparator.
     * @param <T> the type of elements.
     * @return the {@code MergeSortedAsPair} stage.
     */
    public static <T> Graph<FanInShape2<T, T, Pair<T, T>>, NotUsed> getInstance(final T maximalElement,
            final Comparator<T> comparator) {

        return new MergeSortedAsPair<>(maximalElement, comparator);
    }

    @Override
    public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
        return new MergeSortAsPairLogic();
    }

    @Override
    public FanInShape2<T, T, Pair<T, T>> shape() {
        return shape;
    }

    // GraphStageLogic as non-static inner class to have all fields of MergeSortAsPair in scope
    private final class MergeSortAsPairLogic extends GraphStageLogic {

        // TODO: attempt another MergeSortAsPairLogic following the structure of MergeSorted.

        private boolean in1IsWaiting = true;
        private boolean in2IsWaiting = true;
        private boolean in1IsComplete = false;
        private boolean in2IsComplete = false;
        private boolean in1HasFinalElement = false;
        private boolean in2HasFinalElement = false;
        private T in1Element;
        private T in2Element;

        private MergeSortAsPairLogic() {
            super(shape);
            setHandler(in1, new In1Handler());
            setHandler(in2, new In2Handler());
            setHandler(out, eagerTerminateOutput());
        }

        @Override
        public void preStart() {
            demandNext1();
            demandNext2();
        }

        private void reactToInletStateTransition() {
            if (!in1IsWaiting && !in2IsWaiting) {
                emit(out, Pair.create(in1Element, in2Element));
                compareAndDemand();
            }
            // otherwise wait until the other stream has element
        }

        private void padIn1ByMaximalElementAfterCompletion() {
            in1Element = maximalElement;
            in1HasFinalElement = false;
        }

        private void padIn2ByMaximalElementAfterCompletion() {
            in2Element = maximalElement;
            in2HasFinalElement = false;
        }

        private void demandNext1() {
            if (in1IsComplete) {
                padIn1ByMaximalElementAfterCompletion();
            } else {
                in1IsWaiting = true;
                pull(in1);
            }
        }

        private void demandNext2() {
            if (in2IsComplete) {
                padIn2ByMaximalElementAfterCompletion();
            } else {
                in2IsWaiting = true;
                pull(in2);
            }
        }

        private void compareAndDemand() {
            final int comparison = comparator.compare(in1Element, in2Element);
            if (comparison <= 0 || in2IsComplete) {
                demandNext1();
            }
            if (comparison >= 0 || in1IsComplete) {
                demandNext2();
            }
        }

        private void setStatesForIn1Completion() {
            in1IsComplete = true;
            if (in1IsWaiting) {
                in1IsWaiting = false;
                padIn1ByMaximalElementAfterCompletion();
            } else {
                in1HasFinalElement = true;
            }
        }

        private void setStatesForIn2Completion() {
            in2IsComplete = true;
            if (in2IsWaiting) {
                in2IsWaiting = false;
                padIn2ByMaximalElementAfterCompletion();
            } else {
                in2HasFinalElement = true;
            }
        }

        private void checkForFinalStageCompletion() {
            if (in1IsComplete && in2IsComplete) {
                if (in1HasFinalElement || in2HasFinalElement) {
                    // Should not happen. Entering this branch indicates a bug.
                    throw new IllegalStateException(
                            "Contract breach: MergeSortedAsPair.checkForFinalStageCompletion() " +
                                    "called before all input stream elements were emitted."
                    );
                }
                completeStage();
            }
        }

        private final class In1Handler extends AbstractInHandler {

            @Override
            public void onPush() {
                in1Element = grab(in1);
                in1IsWaiting = false;
                reactToInletStateTransition();
            }

            @Override
            public void onUpstreamFinish() {
                setStatesForIn1Completion();
                if (!in2IsComplete || in1HasFinalElement || in2HasFinalElement) {
                    reactToInletStateTransition();
                }
                checkForFinalStageCompletion();
            }
        }

        private final class In2Handler extends AbstractInHandler {

            @Override
            public void onPush() {
                in2Element = grab(in2);
                in2IsWaiting = false;
                reactToInletStateTransition();
            }

            @Override
            public void onUpstreamFinish() {
                setStatesForIn2Completion();
                if (!in1IsComplete || in1HasFinalElement || in2HasFinalElement) {
                    reactToInletStateTransition();
                }
                checkForFinalStageCompletion();
            }
        }
    }
}
