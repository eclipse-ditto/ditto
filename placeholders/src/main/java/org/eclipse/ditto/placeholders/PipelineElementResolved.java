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
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

/**
 * Pipeline elements containing a resolved value.
 */
@Immutable
final class PipelineElementResolved implements PipelineElement {

    private final String value;

    private PipelineElementResolved(final String value) {
        this.value = value;
    }

    static PipelineElement of(final String value) {
        return new PipelineElementResolved(value);
    }

    @Override
    public Type getType() {
        return Type.RESOLVED;
    }

    @Override
    public PipelineElement onResolved(final Function<String, PipelineElement> stringProcessor) {
        return stringProcessor.apply(value);
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
    public <T> T accept(final PipelineElementVisitor<T> visitor) {
        return visitor.resolved(value);
    }

    @Override
    public Iterator<String> iterator() {
        return Collections.singletonList(value).iterator();
    }

    @Override
    public boolean equals(final Object that) {
        if (that instanceof PipelineElementResolved) {
            return Objects.equals(value, ((PipelineElementResolved) that).value);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + value + "]";
    }
}
