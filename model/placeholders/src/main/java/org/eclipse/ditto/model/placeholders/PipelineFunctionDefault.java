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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

/**
 * Provides the {@code fn:default('...')} function implementation.
 */
@Immutable
final class PipelineFunctionDefault implements PipelineFunction {

    static final String FUNCTION_NAME = "default";

    private static final String CONSTANT_PATTERN =
            "\\((('(?<singleQuotedConstant>.*)')|(\"(?<doubleQuotedConstant>.*)\"))\\)";
    private static final String PLACEHOLDER_PATTERN = "\\((?<placeholder>\\w+:.+)\\)";

    private static final String OVERALL_PATTERN_STR = CONSTANT_PATTERN + "|" + PLACEHOLDER_PATTERN;
    private static final Pattern OVERALL_PATTERN = Pattern.compile(OVERALL_PATTERN_STR);

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public DefaultFunctionSignature getSignature() {
        return DefaultFunctionSignature.INSTANCE;
    }

    @Override
    public Optional<String> apply(final Optional<String> value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        if (value.isPresent()) {
            // if previous stage was non-empty: proceed with that
            return value;
        } else {
            // parse + resolve the specified default value:
            final ResolvedFunctionParameter<String> resolvedDefaultParam =
                    parseAndResolve(paramsIncludingParentheses, expressionResolver);
            return Optional.of(resolvedDefaultParam.getValue());
        }
    }

    private ResolvedFunctionParameter<String> parseAndResolve(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final ParameterDefinition<String> defaultValueParam = getSignature().getParameterDefinition(0);
        final Matcher matcher = OVERALL_PATTERN.matcher(paramsIncludingParentheses);
        if (matcher.matches()) {

            String constant = matcher.group("singleQuotedConstant");
            constant = constant != null ? constant : matcher.group("doubleQuotedConstant");
            if (constant != null) {
                return new ResolvedDefaultValueParam(defaultValueParam, constant);
            }

            final String placeholder = matcher.group("placeholder");
            if (placeholder != null) {
                final Optional<String> resolved = expressionResolver.resolveSinglePlaceholder(placeholder);
                return new ResolvedDefaultValueParam(defaultValueParam, resolved.orElse(placeholder));
            }
        }

        throw PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                .build();
    }

    /**
     * Describes the signature of the {@code default('defaultValue')} function.
     */
    private static final class DefaultFunctionSignature implements PipelineFunction.Signature {

        private static final DefaultFunctionSignature INSTANCE = new DefaultFunctionSignature();

        private PipelineFunction.ParameterDefinition<String> defaultValueDescription;

        private DefaultFunctionSignature() {
            defaultValueDescription = new DefaultValueParam();
        }

        @Override
        public List<ParameterDefinition> getParameterDefinitions() {
            return Collections.singletonList(defaultValueDescription);
        }

        @Override
        public <T> ParameterDefinition<T> getParameterDefinition(final int index) {
            if (index == 0) {
                return (ParameterDefinition<T>) defaultValueDescription;
            }
            throw new IllegalArgumentException("Signature does not define a parameter at index '" + index + "'");
        }

        @Override
        public String toString() {
            return renderSignature();
        }
    }

    /**
     * Describes the only param of the {@code default('defaultValue')} function.
     */
    private static final class DefaultValueParam implements PipelineFunction.ParameterDefinition<String> {

        @Override
        public String getName() {
            return "defaultValue";
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String getDescription() {
            return "Specifies the default/fallback value and may be a constant in single or double quotes or a placeholder";
        }
    }

    /**
     * Resolved {@link DefaultValueParam} including the 'defaultValue' value.
     */
    private static final class ResolvedDefaultValueParam
            implements PipelineFunction.ResolvedFunctionParameter<String> {

        private final PipelineFunction.ParameterDefinition<String> definition;
        private String value;

        private ResolvedDefaultValueParam(
                final PipelineFunction.ParameterDefinition<String> definition,
                final String value) {
            this.definition = definition;
            this.value = value;
        }

        @Override
        public PipelineFunction.ParameterDefinition<String> getDefinition() {
            return definition;
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}
