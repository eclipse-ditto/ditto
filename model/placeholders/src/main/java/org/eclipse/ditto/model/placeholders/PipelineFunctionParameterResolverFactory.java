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

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

/**
 * Factory that provides parameter resolvers for functions.
 */
@Immutable
final class PipelineFunctionParameterResolverFactory {

    /**
     * Use this to create a parameter resolver that validates for empty parameters.
     *
     * E.g.
     * <ul>
     *     <li>()</li>
     * </ul>
     */
    static EmptyParameterResolver forEmptyParameters() {
        return new EmptyParameterResolver();
    }

    /**
     * Use this to create a parameter resolver that resolves a string constant.
     *
     * E.g.
     * <ul>
     *     <li>("value")</li>
     *     <li>('value')</li>
     * </ul>
     */
    static SingleParameterResolver forStringParameter() {
        return new SingleParameterResolver(SingleParameterResolver.STRING_CONSTANT_PATTERN_STR);
    }

    /**
     * Use this to create a parameter resolver that resolves a string constant or a placeholder.
     *
     * E.g.
     * <ul>
     *     <li>("value")</li>
     *     <li>('value')</li>
     *     <li>(thing:id)</li>
     * </ul>
     */
    static SingleParameterResolver forStringOrPlaceholderParameter() {
        return new SingleParameterResolver(SingleParameterResolver.STRING_CONSTANT_PATTERN_STR + "|" + SingleParameterResolver.PLACEHOLDER_PATTERN_STR);
    }

    private PipelineFunctionParameterResolverFactory() {
        throw new AssertionError();
    }

    static class SingleParameterResolver implements BiFunction<String, ExpressionResolver, Optional<String>> {

        static final String STRING_CONSTANT_PATTERN_STR =
                "(\\(\\s*'(?<singleQuotedConstant>[^']*)'[^,]*\\))|(\\(\\s*\"(?<doubleQuotedConstant>[^\"]*)\"[^,]*\\))";
        static final String PLACEHOLDER_PATTERN_STR = "\\(\\s*(?<placeholder>\\w+:[^,\\s]+)[^,]*\\)";

        private final Pattern pattern;

        private SingleParameterResolver(final String patternStr) {
            this.pattern = Pattern.compile(patternStr);
        }

        @Override
        public Optional<String> apply(final String paramsIncludingParentheses, final ExpressionResolver expressionResolver) {
            final Matcher matcher = this.pattern.matcher(paramsIncludingParentheses);
            if (matcher.matches()) {

                String constant = matcher.group("singleQuotedConstant");
                constant = constant != null ? constant : matcher.group("doubleQuotedConstant");
                if (constant != null) {
                    return Optional.of(constant);
                }

                final String placeholder = matcher.group("placeholder");
                if (placeholder != null) {
                    return Optional.of(expressionResolver.resolveSinglePlaceholder(placeholder).orElse(placeholder));
                }
            }

            return Optional.empty();
        }

    }

    static class EmptyParameterResolver implements Predicate<String> {
        private static final String EMPTY_PARENTHESES_PATTERN = "\\(\\s*\\)";

        @Override
        public boolean test(final String paramsIncludingParentheses) {
            return paramsIncludingParentheses.matches(EMPTY_PARENTHESES_PATTERN);
        }

    }

}
