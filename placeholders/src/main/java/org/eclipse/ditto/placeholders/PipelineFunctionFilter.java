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
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.placeholders.filter.FilterFunction;
import org.eclipse.ditto.placeholders.filter.FilterFunctions;

/**
 * Provides the {@code fn:filter(filterValue, rqlFunction, comparedValue)} function implementation.
 */
@Immutable
final class PipelineFunctionFilter implements PipelineFunction {

    private static final String FUNCTION_NAME = "filter";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public Signature getSignature() {
        return FilterFunctionSignature.INSTANCE;
    }

    @Override
    public PipelineElement apply(final PipelineElement value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final Parameters parameters = parseAndResolve(paramsIncludingParentheses, expressionResolver);

        return value.onResolved(valueThatShouldBeFilteredConditionally -> {

            final Optional<FilterFunction> rqlFunctionOpt =
                    FilterFunctions.fromName(parameters.getRqlFunction());
            final boolean shouldKeepValue = rqlFunctionOpt
                    .map(rqlFunction -> applyRqlFunction(parameters, rqlFunction))
                    .orElse(false);


            if (shouldKeepValue) {
                return PipelineElement.resolved(valueThatShouldBeFilteredConditionally);
            } else {
                return PipelineElement.unresolved();
            }

        });
    }

    private Boolean applyRqlFunction(final Parameters parameters, final FilterFunction rqlFunction) {
        return parameters.getComparedValueParam()
                .map(comparedValue -> rqlFunction.apply(parameters.getFilterValue(), comparedValue))
                .orElseGet(() -> rqlFunction.apply(parameters.getFilterValue()));
    }

    private Parameters parseAndResolve(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final List<PipelineElement> parameterElements =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter()
                .apply(paramsIncludingParentheses, expressionResolver, this);

        final ParametersBuilder parametersBuilder = new ParametersBuilder();

        final PipelineElement filterValueParamElement = parameterElements.get(0);
        final String filterValueParam = filterValueParamElement.toOptional().orElse("");
        parametersBuilder.withFilterValue(filterValueParam);

        final PipelineElement rqlFunctionParamElement = parameterElements.get(1);
        final String rqlFunctionParam = rqlFunctionParamElement.toOptional().orElseThrow(() ->
                PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                        .build());
        parametersBuilder.withRqlFunction(rqlFunctionParam);

        if (parameterElements.size() > 2) {
            final PipelineElement comparedValueParamElement = parameterElements.get(2);
            final String comparedValueParam = comparedValueParamElement.toOptional().orElse("");
            parametersBuilder.withComparedValue(comparedValueParam);
        }

        return parametersBuilder.build();
    }

    /**
     * Describes the signature of the {@code filter(filterValue, rqlFunction, comparedValue)} function.
     */
    static final class FilterFunctionSignature implements Signature {

        private static final FilterFunctionSignature INSTANCE = new FilterFunctionSignature();

        private final ParameterDefinition<String> filterValueParam;
        private final ParameterDefinition<String> rqlFunctionParam;
        private final ParameterDefinition<String> comparedValueParam;

        private FilterFunctionSignature() {
            filterValueParam = new FilterValueParam();
            rqlFunctionParam = new RqlFunctionParam();
            comparedValueParam = new ComparedValueParam();
        }

        @Override
        public List<ParameterDefinition<?>> getParameterDefinitions() {
            return Arrays.asList(filterValueParam, rqlFunctionParam, comparedValueParam);
        }

        @Override
        public String toString() {
            return renderSignature();
        }

    }

    /**
     * The param that contains the value that should be taken into account for filtering.
     */
    private static final class FilterValueParam implements ParameterDefinition<String> {

        static final String NAME = "filterValue";

        private FilterValueParam() {
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String getDescription() {
            return "Specifies the value that should be taken into account for filtering. " +
                    "It may be a constant in single or double quotes or a placeholder";
        }

    }

    /**
     * Describes param that contains the rql function that should be applied for comparison.
     */
    private static final class RqlFunctionParam implements ParameterDefinition<String> {

        static final String NAME = "rqlFunction";

        private RqlFunctionParam() {
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String getDescription() {
            return "Specifies the rql function that should be applied for comparison.";
        }

    }

    /**
     * The param that contains the value that should compared to {@link FilterValueParam}.
     */
    private static final class ComparedValueParam implements ParameterDefinition<String> {

        static final String NAME = "comparedValue";

        private ComparedValueParam() {
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String getDescription() {
            return "The param that contains the value that should compared to the filter value.";
        }

    }

    @Immutable
    private static final class Parameters {
        private final String filterValue;
        private final String rqlFunction;
        @Nullable
        private final String comparedValueParam;


        private Parameters(
                final String filterValue,
                final String rqlFunction,
                @Nullable final String comparedValueParam) {
            this.filterValue = checkNotNull(filterValue, "filterValue");
            this.rqlFunction = checkNotNull(rqlFunction, "rqlFuntion");
            this.comparedValueParam = comparedValueParam;
        }

        private String getFilterValue() {
            return filterValue;
        }

        private String getRqlFunction() {
            return rqlFunction;
        }

        private Optional<String> getComparedValueParam() {
            return Optional.ofNullable(comparedValueParam);
        }

    }

    private static final class ParametersBuilder {
        private String filterValue;
        private String rqlFunction;
        private String comparedValue;

        ParametersBuilder withFilterValue(final String filterValue) {
            this.filterValue = filterValue;
            return this;
        }

        ParametersBuilder withRqlFunction(final String rqlFunction) {
            this.rqlFunction = rqlFunction;
            return this;
        }

        ParametersBuilder withComparedValue(@Nullable final String comparedValue) {
            this.comparedValue = comparedValue;
            return this;
        }

        Parameters build() {
            return new Parameters(filterValue, rqlFunction, comparedValue);
        }

    }


}
