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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

/**
 * The unique pipeline element signifying failed resolution.
 */
@Immutable
final class PipelineElementUnresolved implements PipelineElement {

    static final PipelineElement INSTANCE = new PipelineElementUnresolved();

    private PipelineElementUnresolved() {
        // no-op
    }

    @Override
    public Type getType() {
        return Type.UNRESOLVED;
    }

    @Override
    public PipelineElement onResolved(final Function<String, PipelineElement> stringProcessor) {
        return this;
    }

    @Override
    public PipelineElement onUnresolved(final Supplier<PipelineElement> nextPipelineElement) {
        return nextPipelineElement.get();
    }

    @Override
    public PipelineElement onDeleted(final Supplier<PipelineElement> nextPipelineElement) {
        return this;
    }

    @Override
    public PipelineElement concat(final PipelineElement pipelineElement) {
        return pipelineElement;
    }

    @Override
    public <T> List<T> evaluate(final PipelineElementVisitor<T> visitor) {
        return Collections.singletonList(visitor.unresolved());
    }

    @Override
    public Iterator<String> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
