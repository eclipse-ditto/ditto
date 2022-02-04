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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

            final boolean shouldKeepAnyValue = applyRqlFunction(parameters);
            if (shouldKeepAnyValue && isJsonArray(valueThatShouldBeFilteredConditionally)) {
                return PipelineElement.resolved(
                        filterBasedOnRqlFunction(valueThatShouldBeFilteredConditionally, parameters)
                );
            } else if (shouldKeepAnyValue) {
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

    private static boolean isJsonArray(final String value) {
        return PipelineElement.JSON_ARRAY_PATTERN.matcher(value).matches();
    }

    private static boolean applyRqlFunction(final Parameters parameters) {

        final Optional<FilterFunction> rqlFunctionOpt = parameters.getRqlFunction();
        if (rqlFunctionOpt.isPresent()) {
            final FilterFunction rqlFunction = rqlFunctionOpt.get();
            final List<String> filterValues = parameters.getFilterValues();
            return parameters.getComparedValueParam()
                    .map(comparedValues -> applyFilter(rqlFunction, filterValues, comparedValues))
                    .orElseGet(() -> applyFilter(rqlFunction, filterValues, null));
        } else {
            return false;
        }
    }

    private static boolean applyFilter(final FilterFunction rqlFunction,
            final List<String> filterValues,
            @Nullable final List<String> compareValues) {

        if (!filterValues.isEmpty() && null != compareValues && !compareValues.isEmpty()) {
            return filterValues.stream().anyMatch(filterValue ->
                    compareValues.stream().anyMatch(compareValue -> rqlFunction.apply(filterValue, compareValue))
            );
        } else if (filterValues.isEmpty() && null != compareValues && compareValues.isEmpty()) {
            return rqlFunction.apply("", "");
        } else if (filterValues.isEmpty() && null != compareValues) {
            return compareValues.stream().anyMatch(compareValue -> rqlFunction.apply("", compareValue));
        } else if (null != compareValues) {
            return filterValues.stream().anyMatch(filterValue -> rqlFunction.apply(filterValue, ""));
        } else {
            return filterValues.stream().anyMatch(rqlFunction::apply);
        }
    }

    private static String filterBasedOnRqlFunction(final String valueThatShouldBeFiltered,
            final Parameters parameters) {

        return PipelineElement.expandJsonArraysInString(valueThatShouldBeFiltered)
                .filter(filterValue -> parameters.getRqlFunction()
                        .map(rqlFunction -> parameters.getComparedValueParam()
                                .map(comparedValues -> comparedValues.stream()
                                        .anyMatch(comparedValue -> rqlFunction.apply(filterValue, comparedValue))
                                )
                                .orElseGet(() -> rqlFunction.apply(filterValue))
                        )
                        .orElse(false)
                ).collect(Collectors.joining("\",\"", "[\"", "\"]"));
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
        parametersBuilder.withComparedValue(comparedElement.toOptionalStream().collect(Collectors.toList()));
        parametersBuilder.withFilterValue(filterElement.toOptionalStream().collect(Collectors.toList()));
        return parametersBuilder.build();
    }

    private static Parameters handleParametersWithOmittedCompared(@Nullable final FilterFunction filter,
            final PipelineElement previousElement) {

        final ParametersBuilder parametersBuilder = new ParametersBuilder();
        parametersBuilder.withRqlFunction(filter);
        parametersBuilder.withFilterValue(previousElement.toOptionalStream().collect(Collectors.toList()));
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

        private final List<String> filterValues;
        @Nullable
        private final FilterFunction rqlFunction;
        @Nullable
        private final List<String> comparedValueParams;


        private Parameters(
                final Collection<String> filterValues,
                @Nullable final FilterFunction rqlFunction,
                @Nullable final Collection<String> comparedValueParams) {

            this.filterValues = Collections.unmodifiableList(new ArrayList<>(
                    checkNotNull(filterValues, "filterValues")));
            this.rqlFunction = rqlFunction;
            if (null != comparedValueParams) {
                this.comparedValueParams = Collections.unmodifiableList(new ArrayList<>(comparedValueParams));
            } else {
                this.comparedValueParams = null;
            }
        }

        private List<String> getFilterValues() {
            return filterValues;
        }

        private Optional<FilterFunction> getRqlFunction() {
            return Optional.ofNullable(rqlFunction);
        }

        private Optional<List<String>> getComparedValueParam() {
            return Optional.ofNullable(comparedValueParams);
        }

    }

    private static final class ParametersBuilder {

        private Collection<String> filterValues;
        @Nullable private FilterFunction rqlFunction;
        @Nullable private Collection<String> comparedValues;

        void withFilterValue(final Collection<String> filterValues) {
            this.filterValues = filterValues;
        }

        void withRqlFunction(@Nullable final FilterFunction rqlFunction) {
            this.rqlFunction = rqlFunction;
        }

        void withComparedValue(@Nullable final Collection<String> comparedValues) {
            this.comparedValues = comparedValues;
        }

        Parameters build() {
            return new Parameters(filterValues, rqlFunction, comparedValues);
        }

    }


}
