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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import javax.annotation.Nullable;

/**
 * Package-private implementation of {@code PipelineElementVisitor}.
 *
 * @param <T> type of visitor result.
 */
final class ImmutablePipelineElementVisitor<T> implements PipelineElementVisitor<T> {

    private final Function<String, T> onResolution;
    private final T onIrresolution;
    private final T onDeletion;

    private ImmutablePipelineElementVisitor(
            final Function<String, T> onResolution,
            final T onIrresolution,
            final T onDeletion) {
        this.onResolution = onResolution;
        this.onIrresolution = onIrresolution;
        this.onDeletion = onDeletion;
    }

    static <T> PipelineElementVisitor.Builder<T> newBuilder() {
        return new Builder<>();
    }

    @Override
    public T resolved(final String resolved) {
        return onResolution.apply(resolved);
    }

    @Override
    public T unresolved() {
        return onIrresolution;
    }

    @Override
    public T deleted() {
        return onDeletion;
    }

    private static final class Builder<T> implements PipelineElementVisitor.Builder<T> {

        @Nullable private Function<String, T> onResolution;
        @Nullable private T onIrresolution;
        @Nullable private T onDeletion;

        private Builder() {}

        @Override
        public PipelineElementVisitor<T> build() {
            return new ImmutablePipelineElementVisitor<>(
                    checkNotNull(onResolution, "onResolution"),
                    checkNotNull(onIrresolution, "onIrresolution"),
                    checkNotNull(onDeletion, "onDeletion"));
        }

        @Override
        public PipelineElementVisitor.Builder<T> resolved(final Function<String, T> onResolution) {
            this.onResolution = onResolution;
            return this;
        }

        @Override
        public PipelineElementVisitor.Builder<T> unresolved(final T onIrresolution) {
            this.onIrresolution = onIrresolution;
            return this;
        }

        @Override
        public PipelineElementVisitor.Builder<T> deleted(final T onDeletion) {
            this.onDeletion = onDeletion;
            return this;
        }
    }
}
