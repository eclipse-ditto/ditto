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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Pipeline elements containing a resolved value.
 */
@Immutable
final class PipelineElementResolved implements PipelineElement {

    private final List<String> values;

    private PipelineElementResolved(final Collection<String> values) {
        this.values = Collections.unmodifiableList(new ArrayList<>(ConditionChecker.checkNotEmpty(values, "values")));
    }

    static PipelineElement of(final Collection<String> values) {
        return new PipelineElementResolved(values);
    }

    @Override
    public Type getType() {
        return Type.RESOLVED;
    }

    @Override
    public PipelineElement onResolved(final Function<String, PipelineElement> stringProcessor) {
        return values.stream()
                .map(stringProcessor)
                .reduce(PipelineElement::concat)
                .orElse(PipelineElement.unresolved());
    }

    @Override
    public PipelineElement onUnresolved(final Supplier<PipelineElement> nextPipelineElement) {
        return this;
    }

    @Override
    public PipelineElement onDeleted(final Supplier<PipelineElement> nextPipelineElement) {
        return this;
    }

    @Override
    public PipelineElement concat(final PipelineElement pipelineElement) {
        if (pipelineElement.getType() == Type.DELETED) {
            return pipelineElement;
        }
        final List<String> concatenatedValues = Stream.concat(toStream(), pipelineElement.toStream())
                .collect(Collectors.toList());
        return PipelineElementResolved.of(concatenatedValues);
    }

    @Override
    public <T> List<T> evaluate(final PipelineElementVisitor<T> visitor) {
        return values.stream()
                .map(visitor::resolved)
                .collect(Collectors.toList());
    }

    @Override
    public Iterator<String> iterator() {
        return values.iterator();
    }

    @Override
    public Stream<String> toStream() {
        return values.stream();
    }

    @Override
    public boolean equals(final Object that) {
        if (that instanceof PipelineElementResolved) {
            return Objects.equals(values, ((PipelineElementResolved) that).values);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + values + "]";
    }
}
