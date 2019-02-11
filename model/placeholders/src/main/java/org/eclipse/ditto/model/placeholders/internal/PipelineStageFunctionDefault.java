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
package org.eclipse.ditto.model.placeholders.internal;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.PlaceholderInvalidException;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;

/**
 * Provides the {@code fn:default('...')} function implementation.
 */
@Immutable
final class PipelineStageFunctionDefault implements PipelineStageFunction {

    private static final String FUNCTION_NAME = "default";

    private static final String CONSTANT_PATTERN =
            "\\((('(?<singleQuotedConstant>.*)')|(\"(?<doubleQuotedConstant>.*)\"))\\)";
    private static final String PLACEHOLDER_PATTERN = "\\((?<placeholder>\\w+:.+)\\)";

    private static final String OVERALL_PATTERN_STR = CONSTANT_PATTERN + "|" + PLACEHOLDER_PATTERN;
    private static final Pattern OVERALL_PATTERN = Pattern.compile(OVERALL_PATTERN_STR);

    private PipelineStageFunction.ParameterDefinition<String> defaultParamValueDescription;

    PipelineStageFunctionDefault() {
        defaultParamValueDescription = new DefaultParam();
    }

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public List<PipelineStageFunction.ParameterDefinition> getSignature() {
        return Collections.singletonList(defaultParamValueDescription);
    }

    /**
     * Parses the passed in {@code paramsIncludingParentheses} by validating whether the "signature" was as expected and
     * finding out the resolved parameter values as List of {@link ResolvedFunctionParameter}s.
     *
     * @param paramsIncludingParentheses  the passed in function parameters including parentheses, e.g.: {@code
     * ('fallback')}
     * @param expressionResolver TODO TJ doc
     * @return the resolved List of {@link ResolvedFunctionParameter}s
     */
    private List<ResolvedFunctionParameter> parseAndResolve(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {
        final String trimmed = paramsIncludingParentheses.trim();

        final Matcher matcher = OVERALL_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            String constant = matcher.group("singleQuotedConstant");
            constant = constant != null ? constant : matcher.group("doubleQuotedConstant");
            if (constant != null) {
                return Collections.singletonList(new ResolvedDefaultParam(defaultParamValueDescription, constant));
            }
            final String placeholder = matcher.group("placeholder");
            if (placeholder != null) {
                final String resolved = expressionResolver.resolveSinglePlaceholder(placeholder,true);
                return Collections.singletonList(new ResolvedDefaultParam(defaultParamValueDescription, resolved));
            }
        }

        // TODO TJ is that the right exception?
        throw PlaceholderInvalidException.newBuilder(trimmed).build();
    }

    @Override
    public Optional<String> apply(final Optional<String> value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        if (value.isPresent()) {
            // if previous stage was non-empty: proceed with that
            return value;
        } else {
            // if previous pipeline stage was empty: parse + resolve the specified default value
            final ResolvedFunctionParameter<String> resolvedDefaultParam =
                    parseAndResolve(paramsIncludingParentheses, expressionResolver).get(0);
            return Optional.of(resolvedDefaultParam.getValue());
        }
    }

    /**
     * Describes the only param of the {@code default('param')} function.
     */
    private static final class DefaultParam implements PipelineStageFunction.ParameterDefinition<String> {

        private DefaultParam() {
        }

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
            return "Specifies the default/fallback value and may be a constant or a placeholder";
        }
    }

    /**
     * Resolved {@link DefaultParam} including the String value.
     */
    private static final class ResolvedDefaultParam implements PipelineStageFunction.ResolvedFunctionParameter<String> {

        private final PipelineStageFunction.ParameterDefinition<String> definition;
        private String value;

        private ResolvedDefaultParam(
                final PipelineStageFunction.ParameterDefinition<String> definition,
                final String value) {
            this.definition = definition;
            this.value = value;
        }

        @Override
        public PipelineStageFunction.ParameterDefinition<String> getSignature() {
            return definition;
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}
