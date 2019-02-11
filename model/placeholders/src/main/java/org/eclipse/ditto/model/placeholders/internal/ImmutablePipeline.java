/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.placeholders.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.placeholders.ExpressionResolver;

/**
 * Immutable implementation of {@link Pipeline}.
 */
@Immutable
final class ImmutablePipeline implements Pipeline {

    private final List<PipelineStage> stages;

    ImmutablePipeline(final List<PipelineStage> stages) {
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
    }

    @Override
    public Optional<String> executeStages(final Optional<String> pipelineInput,
            final ExpressionResolver expressionResolver) {

        Optional<String> stageValue = pipelineInput;
        for (final PipelineStage stage : stages) {
            stageValue = stage.apply(stageValue, expressionResolver);
        }
        return stageValue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutablePipeline)) {
            return false;
        }
        final ImmutablePipeline that = (ImmutablePipeline) o;
        return Objects.equals(stages, that.stages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stages);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "stages=" + stages +
                "]";
    }
}
