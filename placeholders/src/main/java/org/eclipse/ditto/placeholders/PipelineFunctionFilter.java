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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        final boolean filterValuesArePreviousValues = !parameters.getFilterValues().isPresent();
        final boolean shouldKeepAnyValue = applyRqlFunction(parameters);
        return value.onResolved(valueThatShouldBeFilteredConditionally -> {
            final Collection<String> filteredValues =
                    filterBasedOnRqlFunction(valueThatShouldBeFilteredConditionally, parameters);
            if (filterValuesArePreviousValues) {
                return PipelineElement.resolved(filteredValues);
            } else if (shouldKeepAnyValue) {
                return PipelineElement.resolved(valueThatShouldBeFilteredConditionally);
            }
            return PipelineElement.unresolved();
        });
    }

    private static boolean applyRqlFunction(final Parameters parameters) {

        final Optional<FilterFunction> rqlFunctionOpt = parameters.getRqlFunction();
        if (rqlFunctionOpt.isPresent()) {
            final FilterFunction rqlFunction = rqlFunctionOpt.get();
            final List<String> filterValues = parameters.getFilterValues().orElseGet(Collections::emptyList);
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

    private static Collection<String> filterBasedOnRqlFunction(final String valueThatShouldBeFilteredConditionally,
            final Parameters parameters) {
        // eq, exists, like, ne
        return parameters.getFilterValues()
                .orElseGet(() -> Collections.singletonList(valueThatShouldBeFilteredConditionally))
                .stream()
                .filter(filterValue -> parameters.getRqlFunction()
                        .map(rqlFunction -> parameters.getComparedValueParam()
                                .map(comparedValues -> comparedValues.stream()
                                        .anyMatch(comparedValue -> rqlFunction.apply(filterValue, comparedValue))
                                )
                                .orElseGet(() -> rqlFunction.apply(filterValue))
                        )
                        .orElse(false)
                )
                .collect(Collectors.toSet());
    }

    private Parameters parseAndResolve(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final Parameters result;
        final List<PipelineElement> parameterElements =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter()
                        .apply(paramsIncludingParentheses, expressionResolver, this);

        final PipelineElement firstElement = parameterElements.get(0);
        final PipelineElement secondElement = parameterElements.get(1);
        if (parameterElements.size() == 2) {
            final Optional<FilterFunction> firstFilter =
                    firstElement.findFirst().flatMap(FilterFunctions::fromName);
            if (isExistsFilter(secondElement)) {
                final Optional<FilterFunction> secondFilter =
                        getFilterFunctionOrThrow(secondElement, paramsIncludingParentheses);
                result = handleParametersWithOmittedCompared(secondFilter.orElse(null),
                        firstElement);
            } else {
                result = handleParameters(firstFilter.orElse(null), secondElement);
            }
        } else {
            final Optional<FilterFunction> secondFilter =
                    getFilterFunctionOrThrow(secondElement, paramsIncludingParentheses);
            final PipelineElement thirdElement = parameterElements.get(2);
            result = handleParameters(secondFilter.orElse(null), thirdElement, firstElement);
        }
        return result;

    }

    private static boolean isExistsFilter(final PipelineElement element) {
        final Optional<String> second = element.findFirst();
        return second.isPresent() && second.get().equals("exists");
    }

    private Optional<FilterFunction> getFilterFunctionOrThrow(final PipelineElement element, final String params) {
        return FilterFunctions.fromName(element.findFirst().orElseThrow(() ->
                PlaceholderFunctionSignatureInvalidException.newBuilder(params, this)
                        .build()));
    }

    private static Parameters handleParameters(@Nullable final FilterFunction filter,
            final PipelineElement comparedElement, final PipelineElement filterElement) {

        return new ParametersBuilder()
                .withRqlFunction(filter)
                .withComparedValue(comparedElement.toStream().collect(Collectors.toList()))
                .withFilterValue(filterElement.toStream().collect(Collectors.toList()))
                .build();
    }

    private static Parameters handleParameters(@Nullable final FilterFunction filter,
            final PipelineElement comparedElement) {

        return new ParametersBuilder()
                .withRqlFunction(filter)
                .withComparedValue(comparedElement.toStream().collect(Collectors.toList()))
                .build();
    }

    private static Parameters handleParametersWithOmittedCompared(@Nullable final FilterFunction filter,
            final PipelineElement previousElement) {

        return new ParametersBuilder()
                .withRqlFunction(filter)
                .withFilterValue(previousElement.toStream().collect(Collectors.toList()))
                .build();
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
            return "Specifies the optional value that should be taken into account for filtering. " +
                    "It may be a constant in single or double quotes or a placeholder or may be omitted completely " +
                    "in order to apply the filtering on the previous pipeline element instead.";
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

        @Nullable
        private final List<String> filterValues;
        @Nullable
        private final FilterFunction rqlFunction;
        @Nullable
        private final List<String> comparedValueParams;


        private Parameters(
                @Nullable final Collection<String> filterValues,
                @Nullable final FilterFunction rqlFunction,
                @Nullable final Collection<String> comparedValueParams) {

            this.rqlFunction = rqlFunction;
            if (null != filterValues) {
                this.filterValues =
                        Collections.unmodifiableList(new ArrayList<>(filterValues));
            } else {
                this.filterValues = null;
            }
            if (null != comparedValueParams) {
                this.comparedValueParams = Collections.unmodifiableList(new ArrayList<>(comparedValueParams));
            } else {
                this.comparedValueParams = null;
            }
        }

        private Optional<List<String>> getFilterValues() {
            return Optional.ofNullable(filterValues);
        }

        private Optional<FilterFunction> getRqlFunction() {
            return Optional.ofNullable(rqlFunction);
        }

        private Optional<List<String>> getComparedValueParam() {
            return Optional.ofNullable(comparedValueParams);
        }

    }

    private static final class ParametersBuilder {

        @Nullable private Collection<String> filterValues;
        @Nullable private FilterFunction rqlFunction;
        @Nullable private Collection<String> comparedValues;

        ParametersBuilder withFilterValue(final Collection<String> filterValues) {
            this.filterValues = filterValues;
            return this;
        }

        ParametersBuilder withRqlFunction(@Nullable final FilterFunction rqlFunction) {
            this.rqlFunction = rqlFunction;
            return this;
        }

        ParametersBuilder withComparedValue(final Collection<String> comparedValues) {
            this.comparedValues = comparedValues;
            return this;
        }

        Parameters build() {
            return new Parameters(filterValues, rqlFunction, comparedValues);
        }

    }


}
