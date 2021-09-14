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
 * Provides the {@code fn:delete()} function implementation.
 */
@Immutable
final class PipelineFunctionDelete implements PipelineFunction {

    private static final String FUNCTION_NAME = "delete";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public DeleteFunctionSignature getSignature() {
        return DeleteFunctionSignature.INSTANCE;
    }

    @Override
    public PipelineElement apply(final PipelineElement value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        // check if signature matches (empty params!)
        validateOrThrow(paramsIncludingParentheses);
        return PipelineElement.deleted();
    }

    private void validateOrThrow(final String paramsIncludingParentheses) {
        if (!PipelineFunctionParameterResolverFactory.forEmptyParameters().test(paramsIncludingParentheses)) {
            throw PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                    .build();
        }
    }

    /**
     * Describes the signature of the {@code delete()} function.
     */
    private static final class DeleteFunctionSignature implements Signature {

        private static final DeleteFunctionSignature INSTANCE = new DeleteFunctionSignature();

        private DeleteFunctionSignature() {
        }

        @Override
        public List<ParameterDefinition<?>> getParameterDefinitions() {
            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return renderSignature();
        }
    }

}
