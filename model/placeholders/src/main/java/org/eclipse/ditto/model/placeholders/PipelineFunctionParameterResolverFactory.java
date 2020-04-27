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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    private static final SingleParameterResolver STRING_PARAMETER_RESOLVER = new SingleParameterResolver(false);

    private static final SingleParameterResolver STRING_OR_PLACEHOLDER_PARAMETER_RESOLVER =
            new SingleParameterResolver(true);

    private static final ParameterResolver TRIPLE_STRING_OR_PLACEHOLDER_PARAMETER_RESOLVER =
            new ParameterResolver(3, true);


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

    /**
     * Get a parameter resolver that resolves 3 parameters that each could be either a string constant or a placeholder.
     * <p>
     * E.g.
     * <ul>
     * <li>("value", 'otherValue', thing:id)</li>
     * </ul>
     */
    static ParameterResolver forTripleStringOrPlaceholderParameter() {
        return TRIPLE_STRING_OR_PLACEHOLDER_PARAMETER_RESOLVER;
    }

    private PipelineFunctionParameterResolverFactory() {
        throw new AssertionError();
    }

    static class ParameterResolver {

        static final String SINGLE_QUOTED_CONSTANT_GROUP_NAME_PREFIX = "singleQuotedConstant";
        /**
         * Please note the {0,number} which is used to allow named parameters in a for loop.
         * To resolve it, {@link java.text.MessageFormat} is used.
         */
        static final String SINGLE_QUOTED_CONSTANT = String.format("(\\s*+'(?<%s{0,number}>%s)'\\s*+)",
                SINGLE_QUOTED_CONSTANT_GROUP_NAME_PREFIX, PipelineFunction.SINGLE_QUOTED_STRING_CONTENT);

        static final String DOUBLE_QUOTED_CONSTANT_GROUP_NAME_PREFIX = "doubleQuotedConstant";
        /**
         * Please note the {0,number} which is used to allow named parameters in a for loop.
         * To resolve it, {@link java.text.MessageFormat} is used.
         */
        static final String DOUBLE_QUOTED_CONSTANT = String.format("(\\s*+\"(?<%s{0,number}>%s)\"\\s*+)",
                DOUBLE_QUOTED_CONSTANT_GROUP_NAME_PREFIX, PipelineFunction.DOUBLE_QUOTED_STRING_CONTENT);

        /**
         * Because single quotation marks have a special meaning for {@link java.text.MessageFormat}, we need
         * to replace them with two single quotation marks in a row.
         */
        static final MessageFormat STRING_CONSTANT_PATTERN =
                new MessageFormat((SINGLE_QUOTED_CONSTANT + "|" + DOUBLE_QUOTED_CONSTANT).replaceAll("'", "''"));

        static final String PLACEHOLDER_GROUP_NAME_PREFIX = "placeholder";
        static final String PLACEHOLDER_PATTERN =
                String.format("\\s*+(?<%s{0}>\\w+:[^,\\s]+)[^,)]*+", PLACEHOLDER_GROUP_NAME_PREFIX);

        static final String PARAMETER_SEPARATOR = ",";

        static final String OPEN_PARENTHESIS = "\\(";
        static final String CLOSED_PARENTHESIS = "\\)";

        private final Pattern pattern;
        private final int numberOfParameters;

        private ParameterResolver(final int numberOfParameters, final boolean allowPlaceholders) {

            this.numberOfParameters = numberOfParameters;
            final StringBuilder patternBuilder = new StringBuilder(OPEN_PARENTHESIS);

            for (int parameterIndex = 0; parameterIndex < numberOfParameters; parameterIndex++) {
                if (parameterIndex > 0) {
                    patternBuilder.append(PARAMETER_SEPARATOR);
                }
                final String parameterPattern = buildParameterPattern(parameterIndex, allowPlaceholders);
                patternBuilder.append(parameterPattern);
            }

            patternBuilder.append(CLOSED_PARENTHESIS);

            pattern = Pattern.compile(patternBuilder.toString());
        }

        private static String buildParameterPattern(final int parameterIndex, final boolean allowPlaceholders) {
            final String stringConstantParameterPattern = STRING_CONSTANT_PATTERN.format(new Object[]{parameterIndex});
            if (allowPlaceholders) {
                final String placeholderParameterPattern = MessageFormat.format(PLACEHOLDER_PATTERN, parameterIndex);
                return "(?:" + stringConstantParameterPattern + "|" + placeholderParameterPattern + ")";
            } else {
                return "(?:" + stringConstantParameterPattern + ")";
            }
        }

        private static String buildSingleQuotedConstantGroupName(final int parameterIndex) {
            return SINGLE_QUOTED_CONSTANT_GROUP_NAME_PREFIX + parameterIndex;
        }

        private static String buildDoubleQuotedConstantGroupName(final int parameterIndex) {
            return DOUBLE_QUOTED_CONSTANT_GROUP_NAME_PREFIX + parameterIndex;
        }

        private static String buildPlaceholderGroupName(final int parameterIndex) {
            return PLACEHOLDER_GROUP_NAME_PREFIX + parameterIndex;
        }

        private static Optional<PipelineElement> apply(final Matcher matcher, final int parameterIndex,
                final ExpressionResolver expressionResolver) {

            final String singleQuotedStringConstant = matcher.group(buildSingleQuotedConstantGroupName(parameterIndex));

            if (singleQuotedStringConstant != null) {
                return Optional.of(PipelineElement.resolved(singleQuotedStringConstant));
            } else {
                final String doubleQuotedStringConstant =
                        matcher.group(buildDoubleQuotedConstantGroupName(parameterIndex));
                if (doubleQuotedStringConstant != null) {
                    return Optional.of(PipelineElement.resolved(doubleQuotedStringConstant));
                }
            }

            final String placeholder = matcher.group(buildPlaceholderGroupName(parameterIndex));
            if (placeholder != null) {
                return Optional.of(expressionResolver.resolveAsPipelineElement(placeholder));
            }

            return Optional.empty();
        }

        public List<PipelineElement> apply(final String paramsIncludingParentheses,
                final ExpressionResolver resolver,
                final PipelineFunction pipelineFunction) {

            final Matcher matcher = this.pattern.matcher(paramsIncludingParentheses);

            if (matcher.matches()) {
                final ArrayList<PipelineElement> parameters = new ArrayList<>(numberOfParameters);
                for (int parameterIndex = 0; parameterIndex < numberOfParameters; parameterIndex++) {
                    final PipelineElement resolvedParameter =
                            apply(matcher, parameterIndex, resolver).orElseThrow(() -> {
                                throw PlaceholderFunctionSignatureInvalidException.newBuilder(
                                        paramsIncludingParentheses,
                                        pipelineFunction).build();
                            });
                    parameters.add(parameterIndex, resolvedParameter);
                }
                return parameters;
            }

            throw PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, pipelineFunction)
                    .build();
        }

    }

    static class SingleParameterResolver {

        private final ParameterResolver parameterResolver;

        private SingleParameterResolver(final boolean allowPlaceholders) {
            parameterResolver = new ParameterResolver(1, allowPlaceholders);
        }

        public PipelineElement apply(final String paramsIncludingParentheses,
                final ExpressionResolver resolver,
                final PipelineFunction pipelineFunction) {
            return parameterResolver.apply(paramsIncludingParentheses, resolver, pipelineFunction).get(0);
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
