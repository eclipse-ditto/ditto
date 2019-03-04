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

import java.util.List;
import java.util.Optional;

/**
 * Describes a pipeline function with {@code name}, {@code signature}, signature validation capability and
 * function parameter parsing.
 */
interface PipelineFunction {

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
    Optional<String> apply(Optional<String> value, String paramsIncludingParentheses,
            ExpressionResolver expressionResolver);

    /**
     * Defines a function's signature.
     */
    interface Signature {

        /**
         * @return the function's signature as a List of {@link PipelineFunction.ParameterDefinition}s.
         */
        List<PipelineFunction.ParameterDefinition> getParameterDefinitions();

        /**
         * Determines the parameter definition at the passed {@code index} providing the requeste type {@code <T>}.
         *
         * @param index the index of the parameter whose definition to return.
         * @param <T> the type of the parameter.
         * @return the requested typed parameter definition
         */
        <T> PipelineFunction.ParameterDefinition<T> getParameterDefinition(int index);

        /**
         * @return renders a nice String description of the complete signature
         */
        default String renderSignature() {
            final List<ParameterDefinition> parameterDefinitions = getParameterDefinitions();
            final StringBuilder sb = new StringBuilder("(");
            for (int i=0; i<parameterDefinitions.size(); i++) {
                final ParameterDefinition definition = parameterDefinitions.get(i);
                sb.append(definition.getType().getSimpleName());
                sb.append(" ");
                sb.append(definition.getName());
                sb.append(" /* ");
                sb.append(definition.getDescription());
                sb.append(" */");
                if (i+1 < parameterDefinitions.size()) {
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
