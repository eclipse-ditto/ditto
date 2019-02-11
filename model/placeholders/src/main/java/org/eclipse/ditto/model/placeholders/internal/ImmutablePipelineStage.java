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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.placeholders.ExpressionResolver;

/**
 *
 */
final class ImmutablePipelineStage implements PipelineStage {

    private static final List<PipelineStageFunction> SUPPORTED;

    static {

        SUPPORTED = new ArrayList<>();
        SUPPORTED.add(new PipelineStageFunctionDefault()); // fn:default('fallback value')
        SUPPORTED.add(new PipelineStageFunctionSubstringBefore()); // fn:substring-before(':')
    }

    private String expression;

    /**
     * @param expression e.g.: {@code default('fallback value')}
     */
    ImmutablePipelineStage(final String expression) {
        this.expression = expression;
    }

    @Override
    public String getPrefix() {
        return "fn";
    }

    @Override
    public List<String> getSupportedNames() {

        return SUPPORTED.stream()
                .map(PipelineStageFunction::getName)
                .collect(Collectors.toList());
    }

    @Override
    public boolean supports(final String name) {

        // it is sufficient that the passed in name starts with the function name, e.g.: default('foo')
        // the function validates itself whether the remaining part is valid
        return SUPPORTED.stream()
                .map(PipelineStageFunction::getName)
                .anyMatch(name::startsWith);
    }

    @Override
    public String getExpression() {
        return expression;
    }

    @Override
    public Optional<String> apply(final Optional<String> value, final ExpressionResolver expressionResolver) {

        return SUPPORTED.stream()
                .filter(pf -> expression.startsWith(getPrefix() + ":" + pf.getName()))
                .map(pf -> pf.apply(value, expression.replaceFirst(getPrefix() + ":" + pf.getName(), ""), expressionResolver))
                .findFirst()
                .flatMap(o -> o);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutablePipelineStage)) {
            return false;
        }
        final ImmutablePipelineStage that = (ImmutablePipelineStage) o;
        return Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }


    @Override
    public String toString() {
        return expression;
    }
}
