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

    private static final ParameterResolver DOUBLE_OR_TRIPLE_STRING_OR_PLACEHOLDER_PARAMETER_RESOLVER =
            new ParameterResolver(2, 1, true);


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

    /**
     * Get a parameter resolver that resolves 2 or 3 parameters that each could be either a string constant or a placeholder.
     * <p>
     * E.g.
     * <ul>
     * <li>("value", 'otherValue', 'optionalThirdValue')</li>
     * <li>("value", 'otherValue')</li>
     * </ul>
     */
    static ParameterResolver forDoubleOrTripleStringOrPlaceholderParameter() {
        return DOUBLE_OR_TRIPLE_STRING_OR_PLACEHOLDER_PARAMETER_RESOLVER;
    }

    private PipelineFunctionParameterResolverFactory() {
        throw new AssertionError();
    }

    static class ParameterResolver {

        private static final String SINGLE_QUOTED_CONSTANT_GROUP_NAME_PREFIX = "singleQuotedConstant";
        /**
         * Please note the {0,number} which is used to allow named parameters in a for loop.
         * To resolve it, {@link java.text.MessageFormat} is used.
         */
        private static final String SINGLE_QUOTED_CONSTANT = String.format("(\\s*+'(?<%s{0,number}>%s)'\\s*+)",
                SINGLE_QUOTED_CONSTANT_GROUP_NAME_PREFIX, PipelineFunction.SINGLE_QUOTED_STRING_CONTENT);

        private static final String DOUBLE_QUOTED_CONSTANT_GROUP_NAME_PREFIX = "doubleQuotedConstant";
        /**
         * Please note the {0,number} which is used to allow named parameters in a for loop.
         * To resolve it, {@link java.text.MessageFormat} is used.
         */
        private static final String DOUBLE_QUOTED_CONSTANT = String.format("(\\s*+\"(?<%s{0,number}>%s)\"\\s*+)",
                DOUBLE_QUOTED_CONSTANT_GROUP_NAME_PREFIX, PipelineFunction.DOUBLE_QUOTED_STRING_CONTENT);

        /**
         * Because single quotation marks have a special meaning for {@link java.text.MessageFormat}, we need
         * to replace them with two single quotation marks in a row.
         */
        private static final MessageFormat STRING_CONSTANT_PATTERN =
                new MessageFormat((SINGLE_QUOTED_CONSTANT + "|" + DOUBLE_QUOTED_CONSTANT).replaceAll("'", "''"));

        private static final String PLACEHOLDER_GROUP_NAME_PREFIX = "placeholder";
        private static final MessageFormat PLACEHOLDER_PATTERN =
                new MessageFormat(String.format("\\s*+(?<%s{0}>\\w+:[^,\\s]+)[^,)]*+", PLACEHOLDER_GROUP_NAME_PREFIX));

        private static final String PARAMETER_SEPARATOR = ",";

        private static final String OPEN_PARENTHESIS = "\\(";
        private static final String CLOSED_PARENTHESIS = "\\)";

        private final Pattern pattern;
        private final int requiredParameters;
        private final int optionalParameters;

        private ParameterResolver(final int numberOfParameters, final boolean allowPlaceholders) {
            this(numberOfParameters, 0, allowPlaceholders);
        }

        private ParameterResolver(final int requiredParameters, final int optionalParameters, final boolean allowPlaceholders) {

            this.requiredParameters = requiredParameters;
            this.optionalParameters = optionalParameters;

            final StringBuilder patternBuilder = new StringBuilder(OPEN_PARENTHESIS);

            patternBuilder.append(buildParameterPatterns(0, requiredParameters, allowPlaceholders, false));
            patternBuilder.append(buildParameterPatterns(requiredParameters, optionalParameters, allowPlaceholders, true));

            patternBuilder.append(CLOSED_PARENTHESIS);

            pattern = Pattern.compile(patternBuilder.toString());
        }

        private static String buildParameterPatterns(final int startIndex, final int amount,
                final boolean allowPlaceholders, final boolean optionalParameters) {
            final StringBuilder parameters = new StringBuilder();
            for (int parameterIndex = startIndex; parameterIndex < startIndex + amount; parameterIndex++) {
                final StringBuilder singleParam = new StringBuilder();
                if (optionalParameters) {
                    singleParam.append("(?:");
                }

                if (parameterIndex > 0) {
                    singleParam.append(PARAMETER_SEPARATOR);
                }
                final String parameterPattern = buildParameterPattern(parameterIndex, allowPlaceholders);
                singleParam.append(parameterPattern);

                if (optionalParameters) {
                    singleParam.append(")?");
                }
                parameters.append(singleParam);
            }
            return parameters.toString();
        }

        private static String buildParameterPattern(final int parameterIndex, final boolean allowPlaceholders) {
            final String stringConstantParameterPattern = STRING_CONSTANT_PATTERN.format(new Object[]{parameterIndex});
            if (allowPlaceholders) {
                final String placeholderParameterPattern = PLACEHOLDER_PATTERN.format(new Object[]{parameterIndex});
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

            final Matcher matcher = pattern.matcher(paramsIncludingParentheses);

            if (matcher.matches()) {
                final ArrayList<PipelineElement> parameters = new ArrayList<>(requiredParameters);
                parameters.addAll(extractParameters(matcher, resolver, paramsIncludingParentheses, pipelineFunction,
                        0, requiredParameters, false));
                parameters.addAll(extractParameters(matcher, resolver, paramsIncludingParentheses, pipelineFunction,
                        requiredParameters, optionalParameters, true));
                return parameters;
            }

            throw PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, pipelineFunction)
                    .build();
        }

        private List<PipelineElement> extractParameters(final Matcher matcher, final ExpressionResolver resolver,
                final String paramsIncludingParentheses, final PipelineFunction pipelineFunction,
                final int startIndex, final int amount, final boolean optional) {
            final List<PipelineElement> parameters = new ArrayList<>(amount);
            for (int parameterIndex = startIndex; parameterIndex < startIndex + amount; parameterIndex++) {
                final Optional<PipelineElement> applied = apply(matcher, parameterIndex, resolver);
                if (!optional && !applied.isPresent()) {
                    throw PlaceholderFunctionSignatureInvalidException.newBuilder(
                            paramsIncludingParentheses,
                            pipelineFunction).build();
                }
                applied.ifPresent(parameters::add);
            }
            return parameters;
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
