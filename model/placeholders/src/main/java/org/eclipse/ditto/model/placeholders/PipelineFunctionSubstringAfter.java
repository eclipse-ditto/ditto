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
 * Provides the {@code fn:substring-after('...')} function implementation.
 */
@Immutable
final class PipelineFunctionSubstringAfter implements PipelineFunction {

    static final String FUNCTION_NAME = "substring-after";

    private static final String CONSTANT_PATTERN =
            "\\((('(?<singleQuotedConstant>.*)')|(\"(?<doubleQuotedConstant>.*)\"))\\)";

    private static final Pattern OVERALL_PATTERN = Pattern.compile(CONSTANT_PATTERN);

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

        final ResolvedFunctionParameter<String> resolvedSubstringBeforeParam =
                parseAndResolve(paramsIncludingParentheses).get(0);
        final String splitValue = resolvedSubstringBeforeParam.getValue();

        return value.map(previousStage -> {
            if (previousStage.contains(splitValue)) {
                return previousStage.substring(previousStage.indexOf(splitValue) + 1);
            } else {
                return null;
            }
        });
    }

    private List<ResolvedFunctionParameter> parseAndResolve(final String paramsIncludingParentheses) {

        final ParameterDefinition<String> givenStringParam = getSignature().getParameterDefinition(0);
        final Matcher matcher = OVERALL_PATTERN.matcher(paramsIncludingParentheses);
        if (matcher.matches()) {

            String constant = matcher.group("singleQuotedConstant");
            constant = constant != null ? constant : matcher.group("doubleQuotedConstant");
            if (constant != null) {
                return Collections.singletonList(new ResolvedGivenStringParam(givenStringParam, constant));
            }
        }

        throw PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                .build();
    }

    /**
     * Describes the signature of the {@code substring-after('givenString')} function.
     */
    private static final class SubstringAfterFunctionSignature implements Signature {

        private static final SubstringAfterFunctionSignature INSTANCE = new SubstringAfterFunctionSignature();

        private ParameterDefinition<String> givenStringDescription;

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

    /**
     * Resolved {@link GivenStringParam} including the 'givenString' value.
     */
    private static final class ResolvedGivenStringParam implements ResolvedFunctionParameter<String> {

        private final ParameterDefinition<String> definition;
        private String value;

        private ResolvedGivenStringParam(
                final ParameterDefinition<String> definition,
                final String value) {
            this.definition = definition;
            this.value = value;
        }

        @Override
        public ParameterDefinition<String> getDefinition() {
            return definition;
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}
