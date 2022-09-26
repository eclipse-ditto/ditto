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
package org.eclipse.ditto.protocol.adapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.connectivity.ConnectivityCommandAdapterProvider;
import org.eclipse.ditto.protocol.adapter.provider.AcknowledgementAdapterProvider;
import org.eclipse.ditto.protocol.adapter.provider.PolicyCommandAdapterProvider;
import org.eclipse.ditto.protocol.adapter.provider.ThingCommandAdapterProvider;

/**
 * Implements the logic to select the correct {@link org.eclipse.ditto.protocol.adapter.Adapter} from a given {@link org.eclipse.ditto.protocol.Adaptable}.
 */
final class DefaultAdapterResolver implements AdapterResolver {

    private final Function<Adaptable, Adapter<?>> resolver;
    private final AdapterResolverBySignal resolverBySignal;

    DefaultAdapterResolver(final ThingCommandAdapterProvider thingsAdapters,
            final PolicyCommandAdapterProvider policiesAdapters,
            final ConnectivityCommandAdapterProvider connectivityAdapters,
            final AcknowledgementAdapterProvider acknowledgementAdapters,
            final StreamingSubscriptionCommandAdapter streamingSubscriptionCommandAdapter,
            final StreamingSubscriptionEventAdapter streamingSubscriptionEventAdapter) {
        final List<Adapter<?>> adapters = new ArrayList<>();
        adapters.addAll(thingsAdapters.getAdapters());
        adapters.addAll(policiesAdapters.getAdapters());
        adapters.addAll(connectivityAdapters.getAdapters());
        adapters.addAll(acknowledgementAdapters.getAdapters());
        adapters.add(streamingSubscriptionCommandAdapter);
        adapters.add(streamingSubscriptionEventAdapter);
        resolver = computeResolver(adapters);
        resolverBySignal = new AdapterResolverBySignal(thingsAdapters, policiesAdapters, connectivityAdapters,
                acknowledgementAdapters, streamingSubscriptionCommandAdapter, streamingSubscriptionEventAdapter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Adapter<? extends Signal<?>> getAdapter(final Adaptable adaptable) {
        return (Adapter<? extends Signal<?>>) resolver.apply(adaptable);
    }

    @Override
    public Adapter<Signal<?>> getAdapter(final Signal<?> signal, final TopicPath.Channel channel) {
        return resolverBySignal.resolve(signal, channel);
    }

    private static boolean isResponse(final Adaptable adaptable) {
        return adaptable.getPayload().getHttpStatus().isPresent();
    }

    private static <T> List<T> filter(final List<T> list, final Predicate<T> predicate) {
        return list.stream().filter(predicate).collect(Collectors.toList());
    }

    private static <T> T throwUnknownTopicPathException(final Adaptable adaptable) {
        throw UnknownTopicPathException.newBuilder(adaptable.getTopicPath()).build();
    }

    private static <T> T throwAmbiguityDetectedException(final List<Adapter<?>> adapters) {
        // Ambiguity detected: Adapters have overlapping topic paths.
        throw new IllegalStateException("Indistinguishable adapters detected: " + adapters);
    }

    private static <S, T> Function<S, T> constantFunction(final T result) {
        return ignored -> result;
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
    private static <T extends Enum<T>> EnumMapOrFunction<T> dispatchByEnum(
            final List<Adapter<?>> adapters,
            final Class<T> enumClass,
            final T[] enumValues,
            final Function<Adapter<?>, Set<T>> enumExtractor,
            final Function<List<Adapter<?>>, Function<Adaptable, Adapter<?>>> nextStage) {

        final Map<T, List<Adapter<?>>> matchingAdaptersMap = Arrays.stream(enumValues)
                .collect(Collectors.toMap(
                        Function.identity(),
                        enumValue -> filter(adapters, adapter -> enumExtractor.apply(adapter).contains(enumValue))
                ));

        final Optional<List<Adapter<?>>> matchingAdaptersMapHaveIdenticalValues = matchingAdaptersMap.values()
                .stream()
                .map(Optional::of)
                .reduce((list1, list2) -> list1.equals(list2) ? list1 : Optional.empty())
                .flatMap(Function.identity());

        if (matchingAdaptersMapHaveIdenticalValues.isPresent()) {
            return new IsFunction<>(selectMatchedAdapters(matchingAdaptersMapHaveIdenticalValues.get(), nextStage));
        } else {
            final EnumMap<T, Function<Adaptable, Adapter<?>>> enumMap = new EnumMap<>(enumClass);
            matchingAdaptersMap.forEach((enumValue, matchingAdapters) ->
                    enumMap.put(enumValue, selectMatchedAdapters(matchingAdapters, nextStage))
            );
            return new IsEnumMap<>(enumMap);
        }
    }

    private static Function<Adaptable, Adapter<?>> selectMatchedAdapters(
            final List<Adapter<?>> matchingAdapters,
            final Function<List<Adapter<?>>, Function<Adaptable, Adapter<?>>> nextStage) {

        if (matchingAdapters.isEmpty()) {
            return DefaultAdapterResolver::throwUnknownTopicPathException;
        } else if (matchingAdapters.size() == 1) {
            return constantFunction(matchingAdapters.get(0));
        } else {
            return nextStage.apply(matchingAdapters);
        }
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
     * Convert an extracting function for TopicPath into one for Adaptable.
     *
     * @param extractor the extracting function for TopicPath.
     * @return the extracting function for Adaptable.
     */
    private static <T> Function<Adaptable, T> forTopicPath(final Function<TopicPath, T> extractor) {
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
            final List<ResolverStep> resolverSteps) {
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
     *
     * @param adapters all known adapters.
     * @return a function to find an adapter for an adaptable quickly.
     */
    private static Function<Adaptable, Adapter<?>> computeResolver(final List<Adapter<?>> adapters) {
        return computeResolverRecursively(adapters,
                DefaultAdapterResolver::throwAmbiguityDetectedException,
                0,
                Arrays.asList(
                        new ForEnum<>(TopicPath.Group.class, TopicPath.Group.values(), Adapter::getGroups,
                                forTopicPath(TopicPath::getGroup)),
                        new ForEnum<>(TopicPath.Channel.class, TopicPath.Channel.values(), Adapter::getChannels,
                                forTopicPath(TopicPath::getChannel)),
                        new ForEnum<>(TopicPath.Criterion.class, TopicPath.Criterion.values(), Adapter::getCriteria,
                                forTopicPath(TopicPath::getCriterion)),
                        new ForEnumOptional<>(TopicPath.Action.class, TopicPath.Action.values(), Adapter::getActions,
                                forTopicPath(TopicPath::getAction)),
                        new ForEnumOptional<>(TopicPath.SearchAction.class, TopicPath.SearchAction.values(),
                                Adapter::getSearchActions, forTopicPath(TopicPath::getSearchAction)),
                        new ForEnumOptional<>(TopicPath.StreamingAction.class, TopicPath.StreamingAction.values(),
                                Adapter::getStreamingActions, forTopicPath(TopicPath::getStreamingAction)),
                        new ForEnum<>(Bool.class, Bool.values(), Bool.composeAsSet(Adapter::isForResponses),
                                Bool.compose(DefaultAdapterResolver::isResponse)),
                        new ForEnum<>(Bool.class, Bool.values(), Bool.composeAsSet(Adapter::requiresSubject),
                                Bool.compose(adaptable -> adaptable.getTopicPath().getSubject().isPresent())),
                        new ForEnum<>(Bool.class, Bool.values(), Bool.composeAsSet(Adapter::supportsWildcardTopics),
                                Bool.compose(adaptable -> adaptable.getTopicPath().isWildcardTopic()))
                ));
    }

    /**
     * Describe 1 resolver step.
     */
    private interface ResolverStep {

        /**
         * Compute an adapter resolver function by combining with the computation for the adapter resolver after
         * the current selection step.
         *
         * @param currentAdapters the list of adapters to select from.
         * @param nextStep the computation for the next step in the selection process.
         * @return the adapter selector.
         */
        Function<Adaptable, Adapter<?>> combine(
                List<Adapter<?>> currentAdapters,
                Function<List<Adapter<?>>, Function<Adaptable, Adapter<?>>> nextStep);
    }

    /**
     * Describes 1 resolver step that dispatches an Adaptable according to 1 <b>optional</b> attribute of Enum type.
     * Fields of this class are arguments of {@code forEnumOptional} except the current adapters and the next step.
     *
     * @param <T> the type of the distinguishing attribute. Must be an Enum for performance.
     */
    private static final class ForEnumOptional<T extends Enum<T>> implements ResolverStep {

        private final Class<T> enumClass;
        private final T[] enumValues;
        private final Function<Adapter<?>, Set<T>> getSupportedEnums;
        private final Function<Adaptable, Optional<T>> extractEnum;

        private ForEnumOptional(final Class<T> enumClass,
                final T[] enumValues,
                final Function<Adapter<?>, Set<T>> getSupportedEnums,
                final Function<Adaptable, Optional<T>> extractEnum) {
            this.enumClass = enumClass;
            this.enumValues = enumValues;
            this.getSupportedEnums = getSupportedEnums;
            this.extractEnum = extractEnum;
        }

        @Override
        public Function<Adaptable, Adapter<?>> combine(
                final List<Adapter<?>> currentAdapters,
                final Function<List<Adapter<?>>, Function<Adaptable, Adapter<?>>> nextStep) {
            return forEnumOptional(currentAdapters, enumClass, enumValues, getSupportedEnums, extractEnum, nextStep);
        }

        private static <T extends Enum<T>> Function<Adaptable, Adapter<?>> forEnumOptional(
                final List<Adapter<?>> adapters,
                final Class<T> enumClass,
                final T[] enumValues,
                final Function<Adapter<?>, Set<T>> getSupportedEnums,
                final Function<Adaptable, Optional<T>> extractEnum,
                final Function<List<Adapter<?>>, Function<Adaptable, Adapter<?>>> nextStep) {
            final EnumMapOrFunction<T> dispatchByT =
                    DefaultAdapterResolver.dispatchByEnum(adapters, enumClass, enumValues, getSupportedEnums, nextStep);
            // consider adapters that support no enum value to be those that support adaptables without enum values.
            // e. g., search signals for actions, non-search signals for search actions, non-message signals for subjects
            final List<Adapter<?>> noEnumValueAdapters =
                    filter(adapters, adapter -> getSupportedEnums.apply(adapter).isEmpty());
            return dispatchByT.evalByOptional(nextStep.apply(noEnumValueAdapters), extractEnum);
        }
    }

    /**
     * Describes 1 resolver step that dispatches an Adaptable according to 1 attribute of Enum type.
     * Fields of this class are arguments of {@code forEnum} except the current adapters and the next step.
     *
     * @param <T> the type of the distinguishing attribute. Must be an Enum for performance.
     */
    private static final class ForEnum<T extends Enum<T>> implements ResolverStep {

        private final Class<T> enumClass;
        private final T[] enumValues;
        private final Function<Adapter<?>, Set<T>> getSupportedEnums;
        private final Function<Adaptable, T> extractEnum;

        /**
         * Construct a resolver step.
         *
         * @param enumClass the Enum class.
         * @param enumValues all values of the Enum class.
         * @param getSupportedEnums extract the set of supported enum values from an adapter.
         * @param extractEnum extract the enum value from an adaptable to restrict possible adapters.
         */
        private ForEnum(final Class<T> enumClass,
                final T[] enumValues,
                final Function<Adapter<?>, Set<T>> getSupportedEnums,
                final Function<Adaptable, T> extractEnum) {
            this.enumClass = enumClass;
            this.enumValues = enumValues;
            this.getSupportedEnums = getSupportedEnums;
            this.extractEnum = extractEnum;
        }

        @Override
        public Function<Adaptable, Adapter<?>> combine(
                final List<Adapter<?>> currentAdapters,
                final Function<List<Adapter<?>>, Function<Adaptable, Adapter<?>>> nextStep) {
            return forEnum(currentAdapters, enumClass, enumValues, getSupportedEnums, extractEnum, nextStep);
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
            return DefaultAdapterResolver.dispatchByEnum(adapters, enumClass, enumValues, getSupportedEnums, nextStep)
                    .eval(extractEnum);
        }
    }

    private interface EnumMapOrFunction<T> {

        Function<Adaptable, Adapter<?>> eval(Function<Adaptable, T> extractor);

        Function<Adaptable, Adapter<?>> evalByOptional(
                Function<Adaptable, Adapter<?>> emptyResult,
                Function<Adaptable, Optional<T>> optionalEnumExtractor);
    }

    private static final class IsEnumMap<T extends Enum<T>> implements EnumMapOrFunction<T> {

        private final EnumMap<T, Function<Adaptable, Adapter<?>>> enumMap;

        private IsEnumMap(final EnumMap<T, Function<Adaptable, Adapter<?>>> enumMap) {
            this.enumMap = enumMap;
        }

        @Override
        public Function<Adaptable, Adapter<?>> eval(final Function<Adaptable, T> extractor) {
            return evalEnumMap(enumMap, extractor);
        }

        @Override
        public Function<Adaptable, Adapter<?>> evalByOptional(
                final Function<Adaptable, Adapter<?>> emptyResult,
                final Function<Adaptable, Optional<T>> optionalEnumExtractor) {

            return evalEnumMapByOptional(enumMap, emptyResult, optionalEnumExtractor);
        }
    }

    private static final class IsFunction<T> implements EnumMapOrFunction<T> {

        private final Function<Adaptable, Adapter<?>> function;

        private IsFunction(final Function<Adaptable, Adapter<?>> function) {
            this.function = function;
        }

        @Override
        public Function<Adaptable, Adapter<?>> eval(final Function<Adaptable, T> extractor) {
            return function;
        }

        @Override
        public Function<Adaptable, Adapter<?>> evalByOptional(final Function<Adaptable, Adapter<?>> emptyResult,
                final Function<Adaptable, Optional<T>> optionalEnumExtractor) {
            return r -> optionalEnumExtractor.apply(r).isPresent() ? function.apply(r) : emptyResult.apply(r);
        }
    }

    private enum Bool {
        TRUE,
        FALSE;

        private static Bool of(final boolean bool) {
            return bool ? TRUE : FALSE;
        }

        private static <T> Function<T, Bool> compose(final Predicate<T> predicate) {
            return t -> Bool.of(predicate.test(t));
        }

        private static <T> Function<T, Set<Bool>> composeAsSet(final Predicate<T> predicate) {
            return t -> EnumSet.of(Bool.of(predicate.test(t)));
        }
    }

}
