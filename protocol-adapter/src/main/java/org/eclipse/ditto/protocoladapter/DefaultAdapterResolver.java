/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.protocoladapter.provider.PolicyCommandAdapterProvider;
import org.eclipse.ditto.protocoladapter.provider.ThingCommandAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Implements the logic to select the correct {@link Adapter} from a given {@link Adaptable}.
 */
final class DefaultAdapterResolver implements AdapterResolver {

    private final Function<Adaptable, Adapter<?>> resolver;

    public DefaultAdapterResolver(ThingCommandAdapterProvider thingsAdapters,
            PolicyCommandAdapterProvider policiesAdapters) {
        final List<Adapter<?>> adapters =
                new ArrayList<>(thingsAdapters.getAdapters().size() + policiesAdapters.getAdapters().size());
        adapters.addAll(thingsAdapters.getAdapters());
        adapters.addAll(policiesAdapters.getAdapters());
        resolver = computeResolver(adapters);
    }

    @Override
    public Adapter<? extends Signal<?>> getAdapter(final Adaptable adaptable) {
        return resolver.apply(adaptable);
    }

    private static boolean isResponse(final Adaptable adaptable) {
        return adaptable.getPayload().getStatus().isPresent();
    }

    private static <T> List<T> filter(final List<T> list, final Predicate<T> predicate) {
        return list.stream().filter(predicate).collect(Collectors.toList());
    }

    private static <T> List<T> filterNot(final List<T> list, final Predicate<T> predicate) {
        return filter(list, predicate.negate());
    }

    private static <T> T throwUnknownTopicPathException(final Adaptable adaptable) {
        throw UnknownTopicPathException.newBuilder(adaptable.getTopicPath()).build();
    }

    private static <S, T> Function<S, T> constantFunction(final T result) {
        return ignored -> result;
    }

    /**
     * Create an adapter resolution function according to whether an input adaptable is a response.
     * It is the final stage of adapter resolution.
     *
     * @param adapters relevant adapters at this final stage.
     * @return the adapter resolution function.
     */
    private static Function<Adaptable, Adapter<?>> isResponseStep(final List<Adapter<?>> adapters) {
        final List<Adapter<?>> responseAdapters = filter(adapters, Adapter::isForResponses);
        final List<Adapter<?>> nonResponseAdapters = filterNot(adapters, Adapter::isForResponses);
        if (responseAdapters.size() > 1 || nonResponseAdapters.size() > 1) {
            // ambiguous choice - some of the previously selected enums are invalid
            return DefaultAdapterResolver::throwUnknownTopicPathException;
        }
        final Function<Adaptable, Adapter<?>> getResponseAdapter = responseAdapters.isEmpty()
                ? DefaultAdapterResolver::throwUnknownTopicPathException
                : constantFunction(responseAdapters.get(0));
        final Function<Adaptable, Adapter<?>> getNonResponseAdapter = nonResponseAdapters.isEmpty()
                ? DefaultAdapterResolver::throwUnknownTopicPathException
                : constantFunction(nonResponseAdapters.get(0));
        return adaptable -> isResponse(adaptable)
                ? getResponseAdapter.apply(adaptable)
                : getNonResponseAdapter.apply(adaptable);
    }

    /**
     * Create an EnumMap of subroutines for dispatching according to an enum value during adapter resolution.
     *
     * @param adapters the list of relevant adapters.
     * @param enumClass the class of the enum type.
     * @param enumValues all values of the enum type.
     * @param enumExtractor extractor for the set of supported enum values from an adapter.
     * @param nextStage the factory of subroutines in the enum map.
     * @param <T> the enum type.
     * @return the enum map of subroutines.
     */
    private static <T extends Enum<T>> EnumMap<T, Function<Adaptable, Adapter<?>>> dispatchByEnum(
            final List<Adapter<?>> adapters,
            final Class<T> enumClass,
            final T[] enumValues,
            final Function<Adapter<?>, Set<T>> enumExtractor,
            final Function<List<Adapter<?>>, Function<Adaptable, Adapter<?>>> nextStage) {
        final EnumMap<T, Function<Adaptable, Adapter<?>>> map = new EnumMap<>(enumClass);
        for (final T enumValue : enumValues) {
            final List<Adapter<?>> matchingAdapters =
                    filter(adapters, adapter -> enumExtractor.apply(adapter).contains(enumValue));
            final Function<Adaptable, Adapter<?>> enumValueMatchResult;
            if (matchingAdapters.isEmpty()) {
                enumValueMatchResult = DefaultAdapterResolver::throwUnknownTopicPathException;
            } else if (matchingAdapters.size() == 1) {
                enumValueMatchResult = constantFunction(matchingAdapters.get(0));
            } else {
                enumValueMatchResult = nextStage.apply(matchingAdapters);
            }
            map.put(enumValue, enumValueMatchResult);
        }
        return map;
    }

    /**
     * Similar to this#evalEnumMap, but for cases where not all instances of the argument type has a valid enum value.
     *
     * @param enumMap the enum map to evaluate.
     * @param emptyResult the subroutine to evaluate when the argument has no valid enum value.
     * @param optionalEnumExtractor extractor of an optional enum value from arguments.
     * @param <R> the type of arguments.
     * @param <S> the enum type.
     * @param <T> the type of results.
     * @return the function.
     */
    private static <R, S extends Enum<S>, T> Function<R, T> evalEnumMapByOptional(
            final EnumMap<S, Function<R, T>> enumMap,
            final Function<R, T> emptyResult,
            final Function<R, Optional<S>> optionalEnumExtractor) {
        return r -> {
            final Optional<S> optionalEnumValue = optionalEnumExtractor.apply(r);
            if (optionalEnumValue.isPresent()) {
                return enumMap.get(optionalEnumValue.get()).apply(r);
            } else {
                return emptyResult.apply(r);
            }
        };
    }

    /**
     * Create a fast function by evaluating a subroutine stored in an enum map according to the enum value of each
     * function argument.
     *
     * @param enumMap the enum map to evaluate.
     * @param enumExtractor the function to extract an enum value from the argument.
     * @param <R> the type of arguments.
     * @param <S> the enum type.
     * @param <T> the type of results.
     * @return the function.
     */
    private static <R, S extends Enum<S>, T> Function<R, T> evalEnumMap(
            final EnumMap<S, Function<R, T>> enumMap,
            final Function<R, S> enumExtractor) {
        return r -> enumMap.get(enumExtractor.apply(r)).apply(r);
    }

    /**
     * Compute the adapter resolution function according to TopicPath.Action and this#isResponse.
     *
     * @param adapters the list of relevant adapters.
     * @return the adapter resolution function.
     */
    private static Function<Adaptable, Adapter<?>> actionStep(final List<Adapter<?>> adapters) {
        final EnumMap<TopicPath.Action, Function<Adaptable, Adapter<?>>> dispatchByAction =
                dispatchByEnum(adapters, TopicPath.Action.class, TopicPath.Action.values(),
                        Adapter::getActions, DefaultAdapterResolver::isResponseStep);
        return evalEnumMapByOptional(dispatchByAction, isResponseStep(adapters),
                adaptable -> adaptable.getTopicPath().getAction());
    }

    /**
     * Compute the adapter resolver function for current and subsequent enum dimensions.
     *
     * @param adapters the list of relevant adapters at this step.
     * @param enumClass the class of the enum type to dispatch at this step.
     * @param enumValues all values of the enum class.
     * @param getSupportedEnums the function to extract a set of supported enum values from an adapter.
     * @param extractEnum the function to extract the enum value from an adaptable for adapter resolution.
     * @param nextStep the factory for the adapter resolver function for subsequent enum dimensions.
     * @param <T> the enum type.
     * @return the adapter resolver function.
     */
    private static <T extends Enum<T>> Function<Adaptable, Adapter<?>> forEnum(
            final List<Adapter<?>> adapters,
            final Class<T> enumClass,
            final T[] enumValues,
            final Function<Adapter<?>, Set<T>> getSupportedEnums,
            final Function<Adaptable, T> extractEnum,
            final Function<List<Adapter<?>>, Function<Adaptable, Adapter<?>>> nextStep) {
        return evalEnumMap(dispatchByEnum(adapters, enumClass, enumValues, getSupportedEnums, nextStep), extractEnum);
    }

    /**
     * Convert an enum-extracting function for TopicPath into one for Adaptable.
     *
     * @param extractor the enum-extracting function for TopicPath.
     * @return the enum-extracting function for Adaptable.
     */
    private static <T extends Enum<T>> Function<Adaptable, T> forTopicPath(final Function<TopicPath, T> extractor) {
        return extractor.compose(Adaptable::getTopicPath);
    }

    /**
     * Recursive function to compute the adapter resolver function.
     *
     * @param adapters list of all known adapters.
     * @param finalStep the final step after exhausting {@code resolverSteps}.
     * @param i the index of the current resolver step.
     * @param resolverSteps the list of resolver steps, each holding enough information to compute a function that
     * restricts potential adapters according to 1 enum attribute of an adaptable.
     * @return the adapter resolver function.
     */
    private static Function<Adaptable, Adapter<?>> computeResolverRecursively(final List<Adapter<?>> adapters,
            final Function<List<Adapter<?>>, Function<Adaptable, Adapter<?>>> finalStep,
            final int i,
            final List<ResolverStep<?>> resolverSteps) {
        if (i >= resolverSteps.size()) {
            return finalStep.apply(adapters);
        } else {
            final int j = i + 1;
            return resolverSteps.get(i).combine(adapters,
                    nextAdapters -> computeResolverRecursively(nextAdapters, finalStep, j, resolverSteps));
        }
    }

    /**
     * Compute a fast adapter resolution function from a list of known adapters.
     * <p>
     * Has potential to construct exponentially many EnumMap objects and iterating on exponentially many adapter lists,
     * but the Ditto protocol adapters are unambiguous enough such that the total size of all 9 EnumMap objects is 44,
     * and the total length of iterated adapter lists is 59.
     * Details by return order of {@code dispatchByEnum}:
     * <pre>{@code
     * Constructed size-7 EnumMap from 4 adapters for Action
     * Constructed size-5 EnumMap from 5 adapters for Criterion
     * Constructed size-3 EnumMap from 5 adapters for Channel
     * Constructed size-7 EnumMap from 4 adapters for Action
     * Constructed size-5 EnumMap from 6 adapters for Criterion
     * Constructed size-7 EnumMap from 6 adapters for Action
     * Constructed size-5 EnumMap from 8 adapters for Criterion
     * Constructed size-3 EnumMap from 8 adapters for Channel
     * Constructed size-2 EnumMap from 13 adapters for Group
     * Total EnumMap size: 44
     * Total adapter list length: 59
     * }</pre>
     *
     * @param adapters all known adapters.
     * @return a function to find an adapter for an adaptable quickly.
     */
    private static Function<Adaptable, Adapter<?>> computeResolver(final List<Adapter<?>> adapters) {
        return computeResolverRecursively(adapters, DefaultAdapterResolver::actionStep, 0, Arrays.asList(
                new ResolverStep<>(TopicPath.Group.class, TopicPath.Group.values(), Adapter::getGroups,
                        forTopicPath(TopicPath::getGroup)),
                new ResolverStep<>(TopicPath.Channel.class, TopicPath.Channel.values(), Adapter::getChannels,
                        forTopicPath(TopicPath::getChannel)),
                new ResolverStep<>(TopicPath.Criterion.class, TopicPath.Criterion.values(), Adapter::getCriteria,
                        forTopicPath(TopicPath::getCriterion))
        ));
    }

    /**
     * Describe 1 resolver step that dispatches an Adaptable according to 1 attribute of Enum type.
     *
     * @param <T> the type of the distinguishing attribute. Must be an Enum for performance.
     */
    private static final class ResolverStep<T extends Enum<T>> {

        final Class<T> enumClass;
        final T[] enumValues;
        final Function<Adapter<?>, Set<T>> getSupportedEnums;
        final Function<Adaptable, T> extractEnum;

        /**
         * Construct a resolver step.
         *
         * @param enumClass the Enum class.
         * @param enumValues all values of the Enum class.
         * @param getSupportedEnums extract the set of supported enum values from an adapter.
         * @param extractEnum extract the enum value from an adaptable to restrict possible adapters.
         */
        private ResolverStep(final Class<T> enumClass,
                final T[] enumValues,
                final Function<Adapter<?>, Set<T>> getSupportedEnums,
                final Function<Adaptable, T> extractEnum) {
            this.enumClass = enumClass;
            this.enumValues = enumValues;
            this.getSupportedEnums = getSupportedEnums;
            this.extractEnum = extractEnum;
        }

        private Function<Adaptable, Adapter<?>> combine(
                final List<Adapter<?>> currentAdapters,
                final Function<List<Adapter<?>>, Function<Adaptable, Adapter<?>>> nextStep) {
            return forEnum(currentAdapters, enumClass, enumValues, getSupportedEnums, extractEnum, nextStep);
        }
    }
}
