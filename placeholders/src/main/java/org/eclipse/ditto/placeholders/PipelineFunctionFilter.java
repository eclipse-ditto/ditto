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
import java.util.stream.Stream;

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

        final Parameters parameters = parseAndResolve(paramsIncludingParentheses, expressionResolver, value);

        return value.onResolved(valueThatShouldBeFilteredConditionally -> {

            final boolean shouldKeepValue = parameters.getRqlFunction()
                    .map(rqlFunction -> applyRqlFunction(parameters, rqlFunction))
                    .orElse(false);

            if (shouldKeepValue) {
                return PipelineElement.resolved(valueThatShouldBeFilteredConditionally);
            } else {
                return PipelineElement.unresolved();
            }

        });
    }

    @Override
    public Stream<PipelineElement> applyStreaming(final PipelineElement value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        return Stream.of(apply(value, paramsIncludingParentheses, expressionResolver));
    }

    private static Boolean applyRqlFunction(final Parameters parameters, final FilterFunction rqlFunction) {
        return parameters.getComparedValueParam()
                .map(comparedValue -> rqlFunction.apply(parameters.getFilterValue(), comparedValue))
                .orElseGet(() -> rqlFunction.apply(parameters.getFilterValue()));
    }

    private Parameters parseAndResolve(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver, final PipelineElement value) {

        final Parameters result;
        final List<PipelineElement> parameterElements =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter()
                        .apply(paramsIncludingParentheses, expressionResolver, this);

        final PipelineElement firstElement = parameterElements.get(0);
        final PipelineElement secondElement = parameterElements.get(1);
        if (parameterElements.size() == 2) {
            final Optional<FilterFunction> firstFilter = firstElement.toOptional().flatMap(FilterFunctions::fromName);
            if (firstFilter.isPresent() && isExistsExistsFilter(firstElement, secondElement)) {
                result = handleParameters(firstFilter.orElse(null), secondElement, value);
            } else {
                final Optional<FilterFunction> secondFilter =
                        getFilterFunctionOrThrow(secondElement, paramsIncludingParentheses);
                result = handleParametersWithOmittedCompared(secondFilter.orElse(null), firstElement);
            }
        } else {
            final Optional<FilterFunction> secondFilter =
                    getFilterFunctionOrThrow(secondElement, paramsIncludingParentheses);
            final PipelineElement thirdElement = parameterElements.get(2);
            result = handleParameters(secondFilter.orElse(null), thirdElement, firstElement);
        }
        return result;

    }

    private static boolean isExistsExistsFilter(final PipelineElement firstElement,
            final PipelineElement secondElement) {
        final Optional<String> first = firstElement.toOptional();
        final Optional<String> second = secondElement.toOptional();
        return !(first.isPresent() && first.get().equals("exists") &&
                second.isPresent() && second.get().equals("exists"));
    }

    private Optional<FilterFunction> getFilterFunctionOrThrow(final PipelineElement element, final String params) {
        return FilterFunctions.fromName(element.toOptional().orElseThrow(() ->
                PlaceholderFunctionSignatureInvalidException.newBuilder(params, this)
                        .build()));
    }

    private static Parameters handleParameters(@Nullable final FilterFunction filter,
            final PipelineElement comparedElement, final PipelineElement filterElement) {

        final ParametersBuilder parametersBuilder = new ParametersBuilder();
        parametersBuilder.withRqlFunction(filter);
        parametersBuilder.withComparedValue(comparedElement.toOptional().orElse(""));
        parametersBuilder.withFilterValue(filterElement.toOptional().orElse(""));
        return parametersBuilder.build();
    }

    private static Parameters handleParametersWithOmittedCompared(@Nullable final FilterFunction filter,
            final PipelineElement previousElement) {

        final ParametersBuilder parametersBuilder = new ParametersBuilder();
        parametersBuilder.withRqlFunction(filter);
        parametersBuilder.withFilterValue(previousElement.toOptional().orElse(""));
        return parametersBuilder.build();
    }

    /**
     * Describes the signature of the {@code filter(filterValue, rqlFunction, comparedValue)} function.
     */
    static final class FilterFunctionSignature implements Signature {

        private static final Signature INSTANCE = new FilterFunctionSignature();

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
        @Nullable
        private final FilterFunction rqlFunction;
        @Nullable
        private final String comparedValueParam;


        private Parameters(
                final String filterValue,
                @Nullable final FilterFunction rqlFunction,
                @Nullable final String comparedValueParam) {

            this.filterValue = checkNotNull(filterValue, "filterValue");
            this.rqlFunction = rqlFunction;
            this.comparedValueParam = comparedValueParam;
        }

        private String getFilterValue() {
            return filterValue;
        }

        private Optional<FilterFunction> getRqlFunction() {
            return Optional.ofNullable(rqlFunction);
        }

        private Optional<String> getComparedValueParam() {
            return Optional.ofNullable(comparedValueParam);
        }

    }

    private static final class ParametersBuilder {

        private String filterValue;
        private FilterFunction rqlFunction;
        private String comparedValue;

        void withFilterValue(final String filterValue) {
            this.filterValue = filterValue;
        }

        void withRqlFunction(@Nullable final FilterFunction rqlFunction) {
            this.rqlFunction = rqlFunction;
        }

        void withComparedValue(@Nullable final String comparedValue) {
            this.comparedValue = comparedValue;
        }

        Parameters build() {
            return new Parameters(filterValue, rqlFunction, comparedValue);
        }

    }


}
