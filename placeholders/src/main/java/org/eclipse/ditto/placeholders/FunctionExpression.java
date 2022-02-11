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

/**
 * Defines a function expression used in a Pipeline after the "input" stage of a resolved {@link Placeholder}, e.g.
 * function expressions are expressions like {@code fn:starts-with(':')} or {@code fn:default('fallback')}.
 * Used in a pipeline expression like in the following example:
 * <pre>
 * {@code
 * thing:name | fn:substring-before(':') | fn:default(thing:name)
 * }
 * </pre>
 */
interface FunctionExpression extends Expression {

    /**
     * Prefix of function expressions.
     */
    String PREFIX = "fn";

    /**
     * Executes the Stage by passing in a value and returning a resolved result.
     *
     * @param expression the expression string of this stage including prefix, e.g.: {@code fn:substring-before(':')}.
     * @param resolvedInputValue the resolved input value (e.g. via {@link Placeholder}) to process.
     * @param expressionResolver the expressionResolver to use in order to resolve placeholders occurring in the
     * pipeline expression.
     * @return processed output value, or an empty optional if this stage resolved to an empty Optional.
     */
    PipelineElement resolve(String expression, PipelineElement resolvedInputValue,
            ExpressionResolver expressionResolver);
}
