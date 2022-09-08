/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.placeholders;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * An element going through some pipeline of functions.
 */
public interface PipelineElement extends Iterable<String> {

    /**
     * Get the type of this pipeline element.
     *
     * @return the type.
     */
    Type getType();

    /**
     * Advance a resolved value to the next stage. Other elements are unchanged.
     *
     * @param stringProcessor What to do for resolved values.
     * @return the pipeline element at the next pipeline stage.
     */
    PipelineElement onResolved(Function<String, PipelineElement> stringProcessor);

    /**
     * Advance an unresolved value to the next stage. Other elements are unchanged.
     *
     * @param nextPipelineElement supplier of the next pipeline element.
     * @return the pipeline element at the next pipeline stage.
     */
    PipelineElement onUnresolved(Supplier<PipelineElement> nextPipelineElement);

    /**
     * Advance a deleted value to the next stage. Other elements are unchanged.
     *
     * @param nextPipelineElement supplier of the next pipeline element.
     * @return the pipeline element at the next pipeline stage.
     */
    PipelineElement onDeleted(Supplier<PipelineElement> nextPipelineElement);

    /**
     * Concatenates all resolved values of the given pipelineElement with the resolved values of this pipeline element.
     * In case this or the given pipeline element is a {@link Type#DELETED} pipeline element, the result will
     * always be a deleted pipeline element.
     *
     * @param pipelineElement the pipeline element to concatenate
     * @return the pipeline element which holds the concatenated resolved vales.
     * @since 2.4.0
     */
    PipelineElement concat(PipelineElement pipelineElement);

    /**
     * Evaluate this pipeline element by a visitor.
     *
     * @param visitor the visitor.
     * @param <T> the type of results.
     * @return the evaluation result.
     * @since 2.4.0
     */
    <T> List<T> evaluate(PipelineElementVisitor<T> visitor);

    /**
     * Convert a resolved value into another resolved value and leave other elements untouched.
     *
     * @param mapper what to do about the resolved value.
     * @return the mapped resolved value.
     */
    default PipelineElement map(final Function<String, String> mapper) {
        return onResolved(mapper.andThen(PipelineElement::resolved));
    }

    /**
     * Combine 2 pipeline elements such that unresolved elements are replaced by resolved elements, which is in turn
     * replaced by the deletion signifier.
     * <p>
     * Consider all pipeline elements as a simple lattice with PipelineElementDeleted as top and
     * PipelineElementUnresolved as bottom. This function computes a maximal of 2 elements in the lattice.
     * </p>
     *
     * @param other the other element.
     * @return the combined element.
     */
    default PipelineElement orElse(final PipelineElement other) {
        return onDeleted(() -> this)
                .onResolved(s -> this)
                .onUnresolved(() -> other);
    }

    /**
     * @return an optional holding the first resolved value. Empty if no values were resolved.
     * @since 2.4.0
     */
    default Optional<String> findFirst() {
        return toStream().findFirst();
    }

    /**
     * Converts this pipeline element into a stream of resolved strings.
     * If the PipelineElement did not resolve any value, the stream will be empty.
     * Unresolved or deleted elements will result in an empty stream.
     *
     * @return a stream of strings which this pipeline element did resolve.
     * @since 2.4.0
     */
    default Stream<String> toStream() {
        return Stream.empty();
    }

    /**
     * Create a builder of a visitor to evaluate pipeline elements.
     *
     * @param <T> the type of results.
     * @return the visitor builder.
     */
    static <T> PipelineElementVisitor.Builder<T> newVisitorBuilder() {
        return ImmutablePipelineElementVisitor.newBuilder();
    }

    /**
     * Creat a pipeline element containing the resolved values.
     *
     * @param values the resolved values.
     * @return the pipeline element.
     */
    static PipelineElement resolved(final Collection<String> values) {
        return values.isEmpty() ? PipelineElement.unresolved() : PipelineElementResolved.of(values);
    }


    /**
     * Creat a pipeline element containing a resolved value.
     *
     * @param value the resolved value.
     * @return the pipeline element.
     */
    static PipelineElement resolved(final String value) {
        return resolved(Collections.singletonList(value));
    }

    /**
     * Get the unique pipeline element signifying deletion of the whole string containing the pipeline.
     *
     * @return the deleted element.
     */
    static PipelineElement deleted() {
        return PipelineElementDeleted.INSTANCE;
    }

    /**
     * Get the unique pipeline element signifying failed resolution.
     *
     * @return the unresolved element.
     */
    static PipelineElement unresolved() {
        return PipelineElementUnresolved.INSTANCE;
    }

    /**
     * Types of pipeline element.
     */
    enum Type {

        /**
         * Type of the signifier for deletion.
         */
        DELETED,

        /**
         * Type of resolved values.
         */
        RESOLVED,

        /**
         * Type of the signifier for resolution failure.
         */
        UNRESOLVED
    }

}
