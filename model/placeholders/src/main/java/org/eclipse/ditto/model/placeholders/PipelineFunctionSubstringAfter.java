/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.placeholders;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Provides the {@code fn:substring-after('...')} function implementation.
 */
@Immutable
final class PipelineFunctionSubstringAfter implements PipelineFunction {

    private static final String FUNCTION_NAME = "substring-after";

    private final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
            PipelineFunctionParameterResolverFactory.forStringParameter();

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public Signature getSignature() {
        return SubstringAfterFunctionSignature.INSTANCE;
    }

    @Override
    public Optional<String> apply(final Optional<String> value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final String splitValue = parseAndResolve(paramsIncludingParentheses, expressionResolver);

        return value.map(previousStage -> {
            if (previousStage.contains(splitValue)) {
                return previousStage.substring(previousStage.indexOf(splitValue) + 1);
            } else {
                return null;
            }
        });
    }

    private String parseAndResolve(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {
        final Optional<String> resolved = parameterResolver.apply(paramsIncludingParentheses, expressionResolver);
        return resolved.orElseThrow(() ->
                PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                        .build());
    }

    /**
     * Describes the signature of the {@code substring-after('givenString')} function.
     */
    private static final class SubstringAfterFunctionSignature implements Signature {

        private static final SubstringAfterFunctionSignature INSTANCE = new SubstringAfterFunctionSignature();

        private final ParameterDefinition<String> givenStringDescription;

        private SubstringAfterFunctionSignature() {
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
     * Describes the only param of the {@code substring-after('givenString')} function.
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
            return "Specifies the string to use in order to determine the substring after the first occurrence of that given string";
        }

    }

}
