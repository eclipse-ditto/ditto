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
 * Provides the {@code fn:substring-before('...')} function implementation.
 */
@Immutable
final class PipelineFunctionSubstringBefore implements PipelineFunction {

    private static final String FUNCTION_NAME = "substring-before";

    private final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
            PipelineFunctionParameterResolverFactory.forStringParameter();

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public Signature getSignature() {
        return SubstringBeforeFunctionSignature.INSTANCE;
    }

    @Override
    public Optional<String> apply(final Optional<String> value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final String splitValue = parseAndResolve(paramsIncludingParentheses, expressionResolver);

        return value.map(previousStage -> {
            if (previousStage.contains(splitValue)) {
                return previousStage.substring(0, previousStage.indexOf(splitValue));
            } else {
                return null;
            }
        });
    }

    private String parseAndResolve(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        return parameterResolver.apply(paramsIncludingParentheses, expressionResolver)
                .orElseThrow(() ->
                        PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                                .build());
    }

    /**
     * Describes the signature of the {@code substring-before('givenString')} function.
     */
    private static final class SubstringBeforeFunctionSignature implements PipelineFunction.Signature {

        private static final SubstringBeforeFunctionSignature INSTANCE = new SubstringBeforeFunctionSignature();

        private final PipelineFunction.ParameterDefinition<String> givenStringDescription;

        private SubstringBeforeFunctionSignature() {
            givenStringDescription = new GivenStringParam();
        }

        @Override
        public List<ParameterDefinition> getParameterDefinitions() {
            return Collections.singletonList(givenStringDescription);
        }

        @Override
        public <T> ParameterDefinition<T> getParameterDefinition(final int index) {
            if (index == 0) {
                return (ParameterDefinition<T>) givenStringDescription;
            }
            throw new IllegalArgumentException("Signature does not define a parameter at index '" + index + "'");
        }

        @Override
        public String toString() {
            return renderSignature();
        }
    }

    /**
     * Describes the only param of the {@code substring-before('givenString')} function.
     */
    private static final class GivenStringParam implements ParameterDefinition<String> {

        private GivenStringParam() {
        }

        @Override
        public String getName() {
            return "givenString";
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String getDescription() {
            return "Specifies the string to use in order to determine the substring before the first occurrence of that given string";
        }
    }
}
