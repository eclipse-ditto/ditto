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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

/**
 * Provides the {@code fn:split('separator')} function implementation.
 */
@Immutable
public class PipelineFunctionSplit implements PipelineFunction {

    private static final String FUNCTION_NAME = "split";

    private final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
            PipelineFunctionParameterResolverFactory.forStringParameter();

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public Signature getSignature() {
        return SplitFunctionSignature.INSTANCE;
    }

    @Override
    public PipelineElement apply(final PipelineElement value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        // Using the split function in a non-streaming way, returns the first element
        return applyStreaming(value, paramsIncludingParentheses, expressionResolver).findFirst()
                .orElse(PipelineElement.unresolved());
    }

    @Override
    public Stream<PipelineElement> applyStreaming(final PipelineElement value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final String splitValue = parseAndResolve(paramsIncludingParentheses, expressionResolver);
        final Optional<String> optionalValue = value.toOptional();
            if (optionalValue.isPresent() && optionalValue.get().contains(splitValue)) {
                return Arrays.stream(optionalValue.get().split(splitValue)).map(PipelineElement::resolved);
            } else {
                return Stream.of(value);
            }
    }

    private String parseAndResolve(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        return parameterResolver.apply(paramsIncludingParentheses, expressionResolver, this)
                .toOptional()
                .orElseThrow(
                        () -> PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                                .build());
    }

    /**
     * Describes the signature of the {@code substring-before('givenString')} function.
     */
    private static final class SplitFunctionSignature implements PipelineFunction.Signature {

        private static final SplitFunctionSignature INSTANCE = new SplitFunctionSignature();

        private final PipelineFunction.ParameterDefinition<String> givenStringDescription;

        private SplitFunctionSignature() {
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
     * Describes the only param of the {@code split('givenString')} function.
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
            return "Specifies the string to use in order to determine where to split";
        }
    }

}
