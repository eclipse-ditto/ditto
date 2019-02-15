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
package org.eclipse.ditto.model.placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link Pipeline} able to execute its {@link FunctionExpression}s.
 */
@Immutable
final class ImmutablePipeline implements Pipeline {

    private final FunctionExpression functionExpression;
    private final List<String> stageExpressions;

    ImmutablePipeline(final FunctionExpression functionExpression, final List<String> stageExpressions) {
        this.functionExpression = functionExpression;
        this.stageExpressions = Collections.unmodifiableList(new ArrayList<>(stageExpressions));
    }

    @Override
    public Optional<String> execute(final Optional<String> pipelineInput, final ExpressionResolver expressionResolver) {

        Optional<String> stageValue = pipelineInput;
        for (final String expression : stageExpressions) {
            stageValue = functionExpression.resolve(expression, stageValue, expressionResolver);
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
        return Objects.equals(functionExpression, that.functionExpression) &&
                Objects.equals(stageExpressions, that.stageExpressions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(functionExpression, stageExpressions);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "functionExpression=" + functionExpression +
                ", stageExpressions=" + stageExpressions +
                "]";
    }
}
