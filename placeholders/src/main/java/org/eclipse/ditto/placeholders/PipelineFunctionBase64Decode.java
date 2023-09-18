/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import java.util.Base64;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * Provides the {@code fn:base64-decode()} function implementation.
 */
@Immutable
final class PipelineFunctionBase64Decode implements PipelineFunction {

    private static final String FUNCTION_NAME = "base64-decode";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public Base64DecodeSignature getSignature() {
        return Base64DecodeSignature.INSTANCE;
    }

    @Override
    public PipelineElement apply(final PipelineElement value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        // check if signature matches (empty params!)
        validateOrThrow(paramsIncludingParentheses);
        return value.map(v -> new String(Base64.getDecoder().decode(v)));
    }

    private void validateOrThrow(final String paramsIncludingParentheses) {
        if (!PipelineFunctionParameterResolverFactory.forEmptyParameters().test(paramsIncludingParentheses)) {
            throw PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                    .build();
        }
    }

    /**
     * Describes the signature of the {@code base64-decode()} function.
     */
    private static final class Base64DecodeSignature implements Signature {

        private static final Base64DecodeSignature INSTANCE = new Base64DecodeSignature();

        private Base64DecodeSignature() {
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
