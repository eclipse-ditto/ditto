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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * Package-private implementation of {@code PipelineElementVisitor}.
 *
 * @param <T> type of visitor result.
 */
final class ImmutablePipelineElementVisitor<T> implements PipelineElementVisitor<T> {

    private final Function<String, T> onResolution;
    private final Supplier<T> onIrresolution;
    private final Supplier<T> onDeletion;

    private ImmutablePipelineElementVisitor(
            final Function<String, T> onResolution,
            final Supplier<T> onIrresolution,
            final Supplier<T> onDeletion) {
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
        return onIrresolution.get();
    }

    @Override
    public T deleted() {
        return onDeletion.get();
    }

    private static final class Builder<T> implements PipelineElementVisitor.Builder<T> {

        @Nullable private Function<String, T> onResolution;
        @Nullable private Supplier<T> onIrresolution;
        @Nullable private Supplier<T> onDeletion;

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
        public PipelineElementVisitor.Builder<T> unresolved(final Supplier<T> onIrresolution) {
            this.onIrresolution = onIrresolution;
            return this;
        }

        @Override
        public PipelineElementVisitor.Builder<T> deleted(final Supplier<T> onDeletion) {
            this.onDeletion = onDeletion;
            return this;
        }
    }
}
