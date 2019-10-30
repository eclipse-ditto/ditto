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
package org.eclipse.ditto.model.placeholders;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An element going through some pipeline of functions.
 */
public interface PipelineElement extends Iterable<String> {

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
     * Evaluate this pipeline element by a visitor.
     *
     * @param visitor the visitor.
     * @param <T> the type of results.
     * @return the evaluation result.
     */
    <T> T accept(PipelineElementVisitor<T> visitor);

    /**
     * Convert a resolved value into another resolved value and leave other elements untouched.
     *
     * @param mapper what to do about the resolved value.
     * @return the mapped resolved value.
     */
    default PipelineElement map(Function<String, String> mapper) {
        return onResolved(mapper.andThen(PipelineElement::resolved));
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
     * Creat a pipeline element containing a resolved value.
     *
     * @param value the resolved value.
     * @return the pipeline element.
     */
    static PipelineElement resolved(final String value) {
        return PipelineElementResolved.of(value);
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
}
