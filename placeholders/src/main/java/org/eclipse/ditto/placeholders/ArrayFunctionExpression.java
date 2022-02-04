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

import java.util.stream.Stream;

/**
 * Defines a function expression used in an ArrayPipeline after the "input" stage of a resolved {@link Placeholder}, e.g.
 * function expressions are expressions like {@code fn:split(',')} or {@code fn:lower()}.
 * Used in an array pipeline expression like in the following example:
 * <pre>
 * {@code
 * jwt:scope | fn:split(',') | fn:lower()
 * }
 * </pre>
 *
 * @since 2.4.0
 */
public interface ArrayFunctionExpression extends Expression {

    /**
     * Prefix of function expressions.
     */
    String PREFIX = "fn";

    /**
     * Executes the Stage by passing in a value and returning the resolved results.
     *
     * @param expression the expression string of this stage including prefix, e.g.: {@code fn:split(',')}.
     * @param resolvedInputValue the resolved input value (e.g. via {@link Placeholder}) to process.
     * @param expressionResolver the expressionResolver to use in order to resolve placeholders occurring in the
     * pipeline expression.
     * @return a stream of processed output values.
     */
    Stream<PipelineElement> resolve(String expression, PipelineElement resolvedInputValue,
            ExpressionResolver expressionResolver);

}
