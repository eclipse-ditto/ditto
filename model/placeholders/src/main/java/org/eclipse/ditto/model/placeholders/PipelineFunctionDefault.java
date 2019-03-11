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

import javax.annotation.concurrent.Immutable;

/**
 * Provides the {@code fn:default('...')} function implementation.
 */
@Immutable
final class PipelineFunctionDefault implements PipelineFunction {

    private static final String FUNCTION_NAME = "default";

    private final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
            PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

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
            return parseAndResolveThrow(paramsIncludingParentheses, expressionResolver);
        }
    }

    private Optional<String> parseAndResolveThrow(final String paramsIncludingParentheses, final ExpressionResolver expressionResolver) {
        final Optional<String> resolved = this.parameterResolver.apply(paramsIncludingParentheses, expressionResolver);
        if(!resolved.isPresent()) {
            throw PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                    .build();
        }
        return resolved;
    }

    /**
     * Describes the signature of the {@code default('defaultValue')} function.
     */
    private static final class DefaultFunctionSignature implements PipelineFunction.Signature {

        private static final DefaultFunctionSignature INSTANCE = new DefaultFunctionSignature();

        private final PipelineFunction.ParameterDefinition<String> defaultValueDescription;

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

}
