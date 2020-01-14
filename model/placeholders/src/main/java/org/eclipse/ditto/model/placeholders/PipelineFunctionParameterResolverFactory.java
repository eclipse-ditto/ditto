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

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

/**
 * Factory that provides parameter resolvers for functions.
 */
@Immutable
final class PipelineFunctionParameterResolverFactory {

    private static final EmptyParameterResolver EMPTY_PARAMETER_RESOLVER = new EmptyParameterResolver();

    private static final SingleParameterResolver STRING_PARAMETER_RESOLVER =
            new SingleParameterResolver(SingleParameterResolver.STRING_CONSTANT_PATTERN_STR);

    private static final SingleParameterResolver STRING_OR_PLACEHOLDER_PARAMETER_RESOLVER =
            new SingleParameterResolver(SingleParameterResolver.STRING_CONSTANT_PATTERN_STR + "|" +
                    SingleParameterResolver.PLACEHOLDER_PATTERN_STR);


    /**
     * Get a parameter resolver that validates for empty parameters.
     * <p>
     * E.g.
     * <ul>
     * <li>()</li>
     * </ul>
     */
    static EmptyParameterResolver forEmptyParameters() {
        return EMPTY_PARAMETER_RESOLVER;
    }

    /**
     * Get a parameter resolver that resolves a string constant.
     * <p>
     * E.g.
     * <ul>
     * <li>("value")</li>
     * <li>('value')</li>
     * </ul>
     */
    static SingleParameterResolver forStringParameter() {
        return STRING_PARAMETER_RESOLVER;
    }

    /**
     * Get a parameter resolver that resolves a string constant or a placeholder.
     * <p>
     * E.g.
     * <ul>
     * <li>("value")</li>
     * <li>('value')</li>
     * <li>(thing:id)</li>
     * </ul>
     */
    static SingleParameterResolver forStringOrPlaceholderParameter() {
        return STRING_OR_PLACEHOLDER_PARAMETER_RESOLVER;
    }

    private PipelineFunctionParameterResolverFactory() {
        throw new AssertionError();
    }

    static class SingleParameterResolver {

        static final String STRING_CONSTANT_PATTERN_STR = String.format(
                "(\\(\\s*+'(?<singleQuotedConstant>%s)'\\s*+\\))|(\\(\\s*+\"(?<doubleQuotedConstant>%s)\"\\s*+\\))",
                PipelineFunction.SINGLE_QUOTED_STRING_CONTENT,
                PipelineFunction.DOUBLE_QUOTED_STRING_CONTENT);

        static final String PLACEHOLDER_PATTERN_STR = "\\(\\s*+(?<placeholder>\\w+:[^,\\s]+)[^,)]*+\\)";

        private final Pattern pattern;

        private SingleParameterResolver(final String patternStr) {
            this.pattern = Pattern.compile(patternStr);
        }

        public PipelineElement apply(final String paramsIncludingParentheses,
                final ExpressionResolver resolver,
                final PipelineFunction pipelineFunction) {
            final Matcher matcher = this.pattern.matcher(paramsIncludingParentheses);
            if (matcher.matches()) {

                String constant = matcher.group("singleQuotedConstant");
                constant = constant != null ? constant : matcher.group("doubleQuotedConstant");
                if (constant != null) {
                    return PipelineElement.resolved(constant);
                }

                final String placeholder = matcher.group("placeholder");
                if (placeholder != null) {
                    // if resolution fails, interpret the placeholder string as string literal.
                    return resolver.resolveAsPipelineElement(placeholder);
                }
            }

            throw PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, pipelineFunction)
                    .build();
        }

    }

    static class EmptyParameterResolver implements Predicate<String> {

        private static final String EMPTY_PARENTHESES_PATTERN = "\\(\\s*+\\)";

        @Override
        public boolean test(final String paramsIncludingParentheses) {
            return paramsIncludingParentheses.matches(EMPTY_PARENTHESES_PATTERN);
        }

    }

}
