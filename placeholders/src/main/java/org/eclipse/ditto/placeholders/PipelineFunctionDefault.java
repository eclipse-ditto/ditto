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

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * Provides the {@code fn:default('...')} function implementation.
 */
@Immutable
final class PipelineFunctionDefault implements PipelineFunction {

    private static final String FUNCTION_NAME = "default";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public DefaultFunctionSignature getSignature() {
        return DefaultFunctionSignature.INSTANCE;
    }

    @Override
    public PipelineElement apply(final PipelineElement value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        // parse + resolve the specified default value for unresolved placeholders
        // if previous stage does not resolve to a value. deleted pipeline elements remain deleted.
        // evaluate parameter first to fail fast.
        final PipelineElement parameter = parseAndResolveThrow(paramsIncludingParentheses, expressionResolver);
        return value.onUnresolved(() -> parameter);
    }

    private PipelineElement parseAndResolveThrow(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        return PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter()
                .apply(paramsIncludingParentheses, expressionResolver, this);
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
        public List<ParameterDefinition<?>> getParameterDefinitions() {
            return Collections.singletonList(defaultValueDescription);
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
