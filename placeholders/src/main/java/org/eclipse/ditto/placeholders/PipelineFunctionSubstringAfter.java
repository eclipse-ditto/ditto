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

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * Provides the {@code fn:substring-after('...')} function implementation.
 */
@Immutable
final class PipelineFunctionSubstringAfter implements PipelineFunction {

    private static final String FUNCTION_NAME = "substring-after";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public Signature getSignature() {
        return SubstringAfterFunctionSignature.INSTANCE;
    }

    @Override
    public PipelineElement apply(final PipelineElement value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final String splitValue = parseAndResolve(paramsIncludingParentheses, expressionResolver);

        return value.onResolved(previousStage -> {
            if (previousStage.contains(splitValue)) {
                return PipelineElement.resolved(previousStage.substring(previousStage.indexOf(splitValue) + 1));
            } else {
                return PipelineElement.unresolved();
            }
        });
    }

    private String parseAndResolve(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {
        final PipelineElement resolved = PipelineFunctionParameterResolverFactory.forStringParameter()
                .apply(paramsIncludingParentheses, expressionResolver, this);
        return resolved.toOptional().orElseThrow(() ->
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
        public List<ParameterDefinition<?>> getParameterDefinitions() {
            return Collections.singletonList(givenStringDescription);
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
