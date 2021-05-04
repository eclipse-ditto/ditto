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
package org.eclipse.ditto.internal.utils.akka.controlflow;

import java.util.Comparator;
import java.util.List;

import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.FanInShape2;
import akka.stream.Graph;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.SourceShape;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Source;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

/**
 * Performs a merge on 2 sorted streams but instead of delivering the sort result, delivers the tuple before
 * the final comparison.
 * <p>
 * Behavior definition:
 * <pre>{@code
 * akka.stream.scaladsl.MergeSorted
 * }</pre>
 * is behaviorally equivalent as stream component with
 * <pre>{@code
 * MergeSortAsPair ~> Flow.create().mapConcat(pair -> {
 *                        if (pair.first() < pair.second()) {
 *                            return List.of(pair.first());
 *                        } else if (pair.first() > pair.second()) {
 *                            return List.of(pair.second());
 *                        } else {
 *                            // pair.first() == pair.second()
 *                            return List.of(pair.first(), pair.second());
 *                        }
 *                    })
 * }</pre>
 * <p>
 * The first component of each output pair is always an element of the first stream; the second component
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
 * <p>
 *
 * @since 1.1.0
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
     * Merge 2 sources according to a comparator.
     *
     * @param maximalElement the maximal element to append to the source exhausted before the other.
     * @param comparator a comparator of the element type.
     * @param source1 the first source.
     * @param source2 the second source.
     * @param <T> the type of elements.
     * @return the merged source.
     */
    @SuppressWarnings("unchecked") // due to GraphDSL usage
    public static <T> Source<Pair<T, T>, NotUsed> merge(final T maximalElement,
            final Comparator<T> comparator,
            final Source<T, ?> source1,
            final Source<T, ?> source2) {

        final Graph<SourceShape<Pair<T, T>>, NotUsed> graph = GraphDSL.create(builder -> {
            final SourceShape<T> s1 = builder.add(source1);
            final SourceShape<T> s2 = builder.add(source2);
            final FanInShape2<T, T, Pair<T, T>> merge = builder.add(getInstance(maximalElement, comparator));
            builder.from(s1).toInlet(merge.in0());
            builder.from(s2).toInlet(merge.in1());
            return SourceShape.of(merge.out());
        });

        return Source.fromGraph(graph);
    }

    /**
     * Create a {@code MergeSortedAsPair} stage for a comparable type from a maximal element.
     * An element {@code e} is maximal if {@code x.compareTo(e) <= 0} for all instances {@code x} of the element type.
     * However, comparison is never made on the provided "maximal" element; not satisfying maximality
     * will not cause any strange behavior.
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
     * An element {@code e} is maximal for the comparator if {@code comparator.compare(x,e) <= 0}
     * for all instances {@code x} of the element type.
     * However, the comparator is never called on the provided "maximal" element; not satisfying maximality
     * will not cause any strange behavior.
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

    /**
     * GraphStageLogic for MergeSortedAsPair.
     * Adapted from {@code akka.stream.scaladsl.MergeSorted}.
     */
    private final class MergeSortAsPairLogic extends AbstractDittoGraphStageLogic {

        private T lastElement1;
        private T lastElement2;

        private MergeSortAsPairLogic() {
            super(shape);
            setHandler(in1, ignoreTerminateInput());
            setHandler(in2, ignoreTerminateInput());
            setHandler(out, eagerTerminateOutput());
        }

        @Override
        public void preStart() {
            // kick-start the stream by pulling both inlets
            advanceBothInlets();
        }

        private void advanceBothInlets() {
            read(in1,
                    element1 -> {
                        // set lastElement1 to satisfy the precondition of this::in2CompleteAfterGrabbingIn1
                        lastElement1 = element1;
                        read(in2,
                                element2 -> dispatch(element1, element2),
                                this::in2CompleteAfterGrabbingIn1);
                    },
                    this::in1Complete
            );
        }

        // precondition: both elements were grabbed from the inlets
        private void dispatch(final T element1, final T element2) {
            final int comparison = comparator.compare(element1, element2);
            final Pair<T, T> toEmit = Pair.create(element1, element2);
            if (comparison < 0) {
                // store element2 and pull the next one from in1
                lastElement1 = null;
                lastElement2 = element2;
                emit(out, toEmit, this::advanceIn1AfterGrabbingIn2);
            } else if (comparison > 0) {
                // store element1 and pull the next one from in2
                lastElement1 = element1;
                lastElement2 = null;
                emit(out, toEmit, this::advanceIn2AfterGrabbingIn1);
            } else {
                // neither elements will be emitted again - discard and pull the next pair from the inlets
                //
                // While technically unnecessary, the null assignments maintain the invariant that
                // non-null values of lastElement1 and lastElement2 will be emitted at least one more time.
                // This is handy in the debugger.
                lastElement1 = null;
                lastElement2 = null;
                emit(out, toEmit, this::advanceBothInlets);
            }
        }

        // precondition: lastElement2 was grabbed from in2
        private void advanceIn1AfterGrabbingIn2() {
            read(in1, element1 -> dispatch(element1, lastElement2), this::in1CompleteAfterGrabbingIn2);
        }

        // precondition: lastElement1 was grabbed from in1
        private void advanceIn2AfterGrabbingIn1() {
            read(in2, element2 -> dispatch(lastElement1, element2), this::in2CompleteAfterGrabbingIn1);
        }

        // precondition: lastElement2 was grabbed from in2
        private void in1CompleteAfterGrabbingIn2() {
            final T nextElement1 = lastElement1 != null ? lastElement1 : maximalElement;
            emit(out, Pair.create(nextElement1, lastElement2), this::in1Complete);
        }

        // precondition: lastElement1 was grabbed from in1
        private void in2CompleteAfterGrabbingIn1() {
            final T nextElement2 = lastElement2 != null ? lastElement2 : maximalElement;
            emit(out, Pair.create(lastElement1, nextElement2), this::in2Complete);
        }

        private void in1Complete() {
            passAlongMapConcat(in2, out, this::mapIn2ElementsAfterIn1Complete);
        }

        private void in2Complete() {
            passAlongMapConcat(in1, out, this::mapIn1ElementsAfterIn2Complete);
        }

        private List<Pair<T, T>> mapIn2ElementsAfterIn1Complete(final T element2) {
            if (lastElement1 == null) {
                // final element of in1 is nonexistent or has been emitted as the smaller of a pair
                return List.of(Pair.create(maximalElement, element2));
            } else {
                final int comparison = comparator.compare(lastElement1, element2);
                if (comparison < 0) {
                    // final element of in1 is emitted here as the smaller of a pair
                    final T finalElement1 = lastElement1;
                    lastElement1 = null;
                    return List.of(Pair.create(finalElement1, element2), Pair.create(maximalElement, element2));
                } else if (comparison > 0) {
                    return List.of(Pair.create(lastElement1, element2));
                } else {
                    // comparison == 0
                    final T finalElement1 = lastElement1;
                    lastElement1 = null;
                    return List.of(Pair.create(finalElement1, element2));
                }
            }
        }

        private List<Pair<T, T>> mapIn1ElementsAfterIn2Complete(final T element1) {
            if (lastElement2 == null) {
                // final element of in2 is nonexistent or has been emitted as the smaller of a pair
                return List.of(Pair.create(element1, maximalElement));
            } else {
                final int comparison = comparator.compare(element1, lastElement2);
                if (comparison < 0) {
                    return List.of(Pair.create(element1, lastElement2));
                }
                if (comparison > 0) {
                    // final element of in2 is emitted here as the smaller of a pair
                    final T finalElement2 = lastElement2;
                    lastElement2 = null;
                    return List.of(Pair.create(element1, finalElement2), Pair.create(element1, maximalElement));
                } else {
                    // comparison == 0
                    final T finalElement2 = lastElement2;
                    lastElement2 = null;
                    return List.of(Pair.create(element1, finalElement2));
                }
            }
        }
    }

}
