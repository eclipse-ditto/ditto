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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Provides the {@code fn:upper()} function implementation.
 */
@Immutable
final class PipelineFunctionUpper implements PipelineFunction {

    private static final String FUNCTION_NAME = "upper";

    private final PipelineFunctionParameterResolverFactory.EmptyParameterResolver parameterResolver =
            PipelineFunctionParameterResolverFactory.forEmptyParameters();

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public UpperFunctionSignature getSignature() {
        return UpperFunctionSignature.INSTANCE;
    }

    @Override
    public Optional<String> apply(final Optional<String> value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        // check if signature matches (empty params!)
        validateOrThrow(paramsIncludingParentheses);
        return value.map(String::toUpperCase);
    }

    private void validateOrThrow(final String paramsIncludingParentheses) {
        if (!parameterResolver.test(paramsIncludingParentheses)) {
            throw PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                    .build();
        }
    }

    /**
     * Describes the signature of the {@code upper()} function.
     */
    private static final class UpperFunctionSignature implements Signature {

        private static final UpperFunctionSignature INSTANCE = new UpperFunctionSignature();

        private UpperFunctionSignature() {
        }

        @Override
        public List<ParameterDefinition> getParameterDefinitions() {
            return Collections.emptyList();
        }

        @Override
        public <T> ParameterDefinition<T> getParameterDefinition(final int index) {
            throw new IllegalArgumentException("Signature does not define a parameter at index '" + index + "'");
        }

        @Override
        public String toString() {
            return renderSignature();
        }
    }

}
