/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.placeholders.Expression.SEPARATOR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link Pipeline} able to execute its {@link FunctionExpression}s.
 */
@Immutable
final class ImmutableArrayPipeline implements ArrayPipeline {

    private final ArrayFunctionExpression functionExpression;
    private final List<String> stageExpressions;

    ImmutableArrayPipeline(final ArrayFunctionExpression functionExpression, final List<String> stageExpressions) {
        this.functionExpression = functionExpression;
        this.stageExpressions = Collections.unmodifiableList(new ArrayList<>(stageExpressions));
    }

    @Override
    public Stream<PipelineElement> execute(final PipelineElement pipelineInput,
            final ExpressionResolver expressionResolver) {

        Stream<PipelineElement> input = Stream.of(pipelineInput);
        for (final String expression : stageExpressions) {
            input = input.flatMap(element -> combineElements(element,
                    functionExpression.resolve(expression, element, expressionResolver)));
        }
        return input;
    }


    @Override
    public void validate() {
        stageExpressions.stream()
                .map(expression -> expression.replaceFirst(
                        functionExpression.getPrefix() + SEPARATOR, ""))
                .forEach(expression -> {
                    if (!functionExpression.supports(expression)) {
                        throw PlaceholderFunctionUnknownException.newBuilder(expression).build();
                    }
                });
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableArrayPipeline)) {
            return false;
        }
        final ImmutableArrayPipeline that = (ImmutableArrayPipeline) o;
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

    private static Stream<PipelineElement> combineElements(final PipelineElement self,
            final Stream<PipelineElement> other) {

        return other.map(element -> self.onDeleted(() -> self)
                .onUnresolved(() -> element)
                .onResolved(s -> element.onDeleted(() -> element)
                        .onUnresolved(() -> self)
                        .onResolved(resolved -> element)
                ));
    }

}
