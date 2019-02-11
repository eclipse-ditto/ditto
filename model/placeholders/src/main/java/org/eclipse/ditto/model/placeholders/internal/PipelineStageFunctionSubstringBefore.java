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
 * Provides the {@code fn:substring-before('...')} function implementation.
 */
@Immutable
final class PipelineStageFunctionSubstringBefore implements PipelineStageFunction {

    private static final String FUNCTION_NAME = "substring-before";

    private static final String CONSTANT_PATTERN =
            "\\((('(?<singleQuotedConstant>.*)')|(\"(?<doubleQuotedConstant>.*)\"))\\)";

    private static final Pattern OVERALL_PATTERN = Pattern.compile(CONSTANT_PATTERN);

    private ParameterDefinition<String> defaultParamValueDescription;

    PipelineStageFunctionSubstringBefore() {
        defaultParamValueDescription = new SubstringBeforeParam();
    }

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public List<ParameterDefinition> getSignature() {
        return Collections.singletonList(defaultParamValueDescription);
    }

    /**
     * Parses the passed in {@code paramsIncludingParentheses} by validating whether the "signature" was as expected and
     * finding out the resolved parameter values as List of {@link ResolvedFunctionParameter}s.
     *
     * @param paramsIncludingParentheses  the passed in function parameters including parentheses, e.g.: {@code
     * ('fallback')}
     * @return the resolved List of {@link ResolvedFunctionParameter}s
     */
    private List<ResolvedFunctionParameter> parseAndResolve(final String paramsIncludingParentheses) {
        final String trimmed = paramsIncludingParentheses.trim();

        final Matcher matcher = OVERALL_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            String constant = matcher.group("singleQuotedConstant");
            constant = constant != null ? constant : matcher.group("doubleQuotedConstant");
            if (constant != null) {
                return Collections.singletonList(new ResolvedDefaultParam(defaultParamValueDescription, constant));
            }
        }

        // TODO TJ is that the right exception?
        // TODO TJ create new exception for wrong function parameters usage
        throw PlaceholderInvalidException.newBuilder(trimmed).build();
    }

    @Override
    public Optional<String> apply(final Optional<String> value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final ResolvedFunctionParameter<String> resolvedSubstringBeforeParam =
                parseAndResolve(paramsIncludingParentheses).get(0);
        final String splitValue = resolvedSubstringBeforeParam.getValue();

        return value.map(previousStage -> {
            if (previousStage.contains(splitValue)) {
                return previousStage.substring(0, previousStage.indexOf(splitValue));
            } else {
                return null;
            }
        });
    }

    /**
     * Describes the only param of the {@code substring-before('param')} function.
     */
    private static final class SubstringBeforeParam implements ParameterDefinition<String> {

        private SubstringBeforeParam() {
        }

        @Override
        public String getName() {
            return "splitCharSequence";
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String getDescription() {
            return "Specifies the character sequence before to split";
        }
    }

    /**
     * Resolved {@link SubstringBeforeParam} including the String value.
     */
    private static final class ResolvedDefaultParam implements ResolvedFunctionParameter<String> {

        private final ParameterDefinition<String> definition;
        private String value;

        private ResolvedDefaultParam(
                final ParameterDefinition<String> definition,
                final String value) {
            this.definition = definition;
            this.value = value;
        }

        @Override
        public ParameterDefinition<String> getSignature() {
            return definition;
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}
