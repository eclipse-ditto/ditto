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

import java.util.List;

/**
 * Describes a pipeline function with {@code name}, {@code signature}, signature validation capability and
 * function parameter parsing.
 */
interface PipelineFunction {

    /**
     * Pattern for content of single-quoted strings.
     */
    String SINGLE_QUOTED_STRING_CONTENT = "(?:\\\\'|[^'])*+";

    /**
     * Pattern for content of double-quoted strings.
     */
    String DOUBLE_QUOTED_STRING_CONTENT = "(?:\\\\\"|[^\"])*+";

    /**
     * @return the function name
     */
    String getName();

    /**
     * @return the function's signature
     */
    Signature getSignature();

    /**
     * Executes the function by passing in a value and returning a processed result.
     *
     * @param value the input value to process.
     * @param paramsIncludingParentheses the passed in function parameters including parentheses, e.g.: {@code
     * ('fallback')}
     * @param expressionResolver the expressionResolver to use in order to resolve placeholders occurring in the
     * function.
     * @return processed output value, or an empty optional otherwise.
     */
    PipelineElement apply(PipelineElement value, String paramsIncludingParentheses,
            ExpressionResolver expressionResolver);

    /**
     * Defines a function's signature.
     */
    interface Signature {

        /**
         * @return the function's signature as a List of {@link PipelineFunction.ParameterDefinition}s.
         */
        List<PipelineFunction.ParameterDefinition<?>> getParameterDefinitions();

        /**
         * @return renders a nice String description of the complete signature
         */
        default String renderSignature() {
            final List<ParameterDefinition<?>> parameterDefinitions = getParameterDefinitions();
            final StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < parameterDefinitions.size(); i++) {
                final ParameterDefinition<?> definition = parameterDefinitions.get(i);
                sb.append(definition.getType().getSimpleName());
                sb.append(" ");
                sb.append(definition.getName());
                sb.append(" /* ");
                sb.append(definition.getDescription());
                sb.append(" */");
                if (i + 1 < parameterDefinitions.size()) {
                    sb.append(" , ");
                }
            }
            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * Defines one parameter in the signature of the function describing its {@code name}, {@code type} and
     * {@code description}.
     *
     * @param <T> the parameter's type
     */
    interface ParameterDefinition<T> {

        /**
         * @return the name of the parameter
         */
        String getName();

        /**
         * @return the type of the parameter
         */
        Class<T> getType();

        /**
         * @return the description of the parameter
         */
        String getDescription();

    }

}
