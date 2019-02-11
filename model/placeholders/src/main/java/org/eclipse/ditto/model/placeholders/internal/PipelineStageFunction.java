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

import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.model.placeholders.ExpressionResolver;

/**
 * Describes a pipeline function with {@code name}, {@code signature} and signature validation capability.
 */
interface PipelineStageFunction {

    /**
     * @return the function name
     */
    String getName();

    /**
     * @return the function's signature as a List of {@link PipelineStageFunction.ParameterDefinition}s.
     */
    List<PipelineStageFunction.ParameterDefinition> getSignature();

    /**
     * Executes the function by passing in a value and returning a processed result.
     *
     * @param value the input value to process.
     * @param paramsIncludingParentheses the passed in function parameters including parentheses, e.g.: {@code
     * ('fallback')}
     * @param expressionResolver TODO TJ doc
     * @return processed output value, or an empty optional otherwise.
     */
    Optional<String> apply(Optional<String> value, String paramsIncludingParentheses,
            ExpressionResolver expressionResolver);

    /**
     * TODO TJ Doc
     * @param <T>
     */
    interface ParameterDefinition<T> {

        String getName();

        Class<T> getType();

        String getDescription();

    }

    /**
     * TODO TJ doc
     * @param <T>
     */
    interface ResolvedFunctionParameter<T> {

        PipelineStageFunction.ParameterDefinition<T> getSignature();

        T getValue();

    }
}
