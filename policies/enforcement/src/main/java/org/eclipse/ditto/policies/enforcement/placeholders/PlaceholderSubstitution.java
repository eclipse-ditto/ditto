/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement.placeholders;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.policies.enforcement.PreEnforcer;
import org.eclipse.ditto.policies.enforcement.placeholders.strategies.SubstitutionStrategy;
import org.eclipse.ditto.policies.enforcement.placeholders.strategies.SubstitutionStrategyRegistry;

/**
 * A function which applies substitution of placeholders on a command (subtype of {@link DittoHeadersSettable}) based on
 * its {@link DittoHeaders}.
 *
 * @param <T> the subtype of {@link DittoHeadersSettable} handled by the PlaceholderSubstitution.
 */
@Immutable
public final class PlaceholderSubstitution<T extends DittoHeadersSettable<?>> implements PreEnforcer<T> {

    private final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm;
    private final SubstitutionStrategyRegistry substitutionStrategyRegistry;

    private PlaceholderSubstitution(final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm,
            final SubstitutionStrategyRegistry substitutionStrategyRegistry) {

        this.substitutionAlgorithm = substitutionAlgorithm;
        this.substitutionStrategyRegistry = substitutionStrategyRegistry;
    }

    /**
     * Creates a new instance with default replacement definitions.
     *
     * @return the created instance.
     * @see #newExtendedInstance(java.util.Map)
     */
    public static <T extends DittoHeadersSettable<?>> PlaceholderSubstitution<T> newInstance() {
        final Map<String, Function<DittoHeaders, String>> defaultReplacementDefinitions =
                createDefaultReplacementDefinitions();

        return createInstance(defaultReplacementDefinitions);
    }

    /**
     * Creates a new instance with default replacement definitions, extended with
     * {@code additionalReplacementDefinitions}.
     *
     * @param additionalReplacementDefinitions the additional replacement definitions.
     * @return the created instance.
     * @see #newInstance()
     */
    public static <T extends DittoHeadersSettable<?>> PlaceholderSubstitution<T> newExtendedInstance(
            final Map<String, Function<DittoHeaders, String>> additionalReplacementDefinitions) {
        requireNonNull(additionalReplacementDefinitions);

        final Map<String, Function<DittoHeaders, String>> defaultReplacementDefinitions =
                createDefaultReplacementDefinitions();

        final Map<String, Function<DittoHeaders, String>> allReplacementDefinitions =
                new LinkedHashMap<>();
        allReplacementDefinitions.putAll(defaultReplacementDefinitions);
        allReplacementDefinitions.putAll(additionalReplacementDefinitions);

        return createInstance(allReplacementDefinitions);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    public CompletionStage<T> apply(final T dittoHeadersSettable) {
        requireNonNull(dittoHeadersSettable);

        final Optional<SubstitutionStrategy> firstMatchingStrategyOpt =
                substitutionStrategyRegistry.getMatchingStrategy(dittoHeadersSettable);
        if (firstMatchingStrategyOpt.isPresent()) {
            final SubstitutionStrategy<T> firstMatchingStrategy = firstMatchingStrategyOpt.get();
            return CompletableFuture.supplyAsync(() ->
                    firstMatchingStrategy.apply(dittoHeadersSettable, substitutionAlgorithm)
            ).thenApply(Function.identity());
        } else {
            return CompletableFuture.completedFuture(dittoHeadersSettable);
        }
    }

    private static Map<String, Function<DittoHeaders, String>> createDefaultReplacementDefinitions() {
        final Map<String, Function<DittoHeaders, String>> defaultReplacementDefinitions = new LinkedHashMap<>();
        defaultReplacementDefinitions.put(SubjectIdReplacementDefinition.REPLACER_NAME,
                SubjectIdReplacementDefinition.getInstance());
        defaultReplacementDefinitions.put(SubjectIdReplacementDefinition.LEGACY_REPLACER_NAME,
                SubjectIdReplacementDefinition.getInstance());
        return Collections.unmodifiableMap(defaultReplacementDefinitions);
    }

    private static <T extends DittoHeadersSettable<?>> PlaceholderSubstitution<T> createInstance(
            final Map<String, Function<DittoHeaders, String>> replacementDefinitions) {
        final HeaderBasedPlaceholderSubstitutionAlgorithm algorithm =
                HeaderBasedPlaceholderSubstitutionAlgorithm.newInstance(replacementDefinitions);
        final SubstitutionStrategyRegistry substitutionStrategyRegistry =
                SubstitutionStrategyRegistry.newInstance();

        return new PlaceholderSubstitution<>(algorithm, substitutionStrategyRegistry);
    }
}
