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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * Provides the {@code fn:replace('from', 'to')} function implementation.
 */
@Immutable
final class PipelineFunctionReplace implements PipelineFunction {

    private static final String FUNCTION_NAME = "replace";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public Signature getSignature() {
        return ReplaceFunctionSignature.INSTANCE;
    }

    @Override
    public PipelineElement apply(final PipelineElement value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final Parameters parameters = parseAndResolve(paramsIncludingParentheses, expressionResolver);

        return value.map(str -> str.replace(parameters.getFrom(), parameters.getTo()));
    }

    private PipelineFunctionReplace.Parameters parseAndResolve(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final List<PipelineElement> parameterElements =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter()
                        .apply(paramsIncludingParentheses, expressionResolver, this);

        final PipelineFunctionReplace.ParametersBuilder parametersBuilder =
                new PipelineFunctionReplace.ParametersBuilder();

        final PipelineElement fromParamElement = parameterElements.get(0);
        final String fromParam = fromParamElement.findFirst().orElse("");
        parametersBuilder.withFrom(fromParam);

        final PipelineElement toParamElement = parameterElements.get(1);
        final String toParam = toParamElement.findFirst().orElseThrow(() ->
                PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                        .build());
        parametersBuilder.withTo(toParam);

        return parametersBuilder.build();
    }

    /**
     * Describes the signature of the {@code replace('from', 'to')} function.
     */
    private static final class ReplaceFunctionSignature implements Signature {

        private static final ReplaceFunctionSignature INSTANCE = new ReplaceFunctionSignature();

        private final ParameterDefinition<String> fromStringDescription;
        private final ParameterDefinition<String> toStringDescription;

        private ReplaceFunctionSignature() {
            fromStringDescription = new FromStringParam();
            toStringDescription = new ToStringParam();
        }

        @Override
        public List<ParameterDefinition<?>> getParameterDefinitions() {
            return Arrays.asList(fromStringDescription, toStringDescription);
        }

        @Override
        public String toString() {
            return renderSignature();
        }

    }

    /**
     * Describes the from param of the {@code replace('from', 'to')} function.
     */
    private static final class FromStringParam implements ParameterDefinition<String> {

        private FromStringParam() {
        }

        @Override
        public String getName() {
            return "from";
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String getDescription() {
            return "Specifies the string to search for and replace";
        }

    }

    /**
     * Describes the to param of the {@code replace('from', 'to')} function.
     */
    private static final class ToStringParam implements ParameterDefinition<String> {

        private ToStringParam() {
        }

        @Override
        public String getName() {
            return "to";
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String getDescription() {
            return "Specifies the replace string to be inserted for all matching 'from' strings";
        }

    }


    @Immutable
    private static final class Parameters {

        private final String from;
        private final String to;

        private Parameters(
                final String from,
                final String to) {
            this.from = checkNotNull(from, "from");
            this.to = checkNotNull(to, "to");
        }

        private String getFrom() {
            return from;
        }

        private String getTo() {
            return to;
        }

    }

    private static final class ParametersBuilder {

        private String from;
        private String to;

        PipelineFunctionReplace.ParametersBuilder withFrom(final String from) {
            this.from = from;
            return this;
        }

        PipelineFunctionReplace.ParametersBuilder withTo(final String to) {
            this.to = to;
            return this;
        }

        PipelineFunctionReplace.Parameters build() {
            return new PipelineFunctionReplace.Parameters(from, to);
        }

    }


}
