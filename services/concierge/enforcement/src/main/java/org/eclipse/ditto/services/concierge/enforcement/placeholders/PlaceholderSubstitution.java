/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies.SubstitutionStrategy;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies.SubstitutionStrategyRegistry;

/**
 * A function which applies substitution of placeholders an a command (subtype of {@link WithDittoHeaders}) based on
 * its {@link DittoHeaders}.
 */
@Immutable
public final class PlaceholderSubstitution implements Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>>  {
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
     * @see #newExtendedInstance(Map)
     */
    public static PlaceholderSubstitution newInstance() {
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
    public static PlaceholderSubstitution newExtendedInstance(
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
    public CompletionStage<WithDittoHeaders> apply(final WithDittoHeaders withDittoHeaders) {
        requireNonNull(withDittoHeaders);

        final Optional<SubstitutionStrategy> firstMatchingStrategyOpt =
                substitutionStrategyRegistry.getMatchingStrategy(withDittoHeaders);
        if (firstMatchingStrategyOpt.isPresent()) {
            final SubstitutionStrategy firstMatchingStrategy = firstMatchingStrategyOpt.get();
            return CompletableFuture.supplyAsync(() -> {
                @SuppressWarnings("unchecked")
                final WithDittoHeaders maybeSubstituted =
                        firstMatchingStrategy.apply(withDittoHeaders, substitutionAlgorithm);

                return maybeSubstituted;
            });
        } else {
            return CompletableFuture.completedFuture(withDittoHeaders);
        }
    }

    private static Map<String, Function<DittoHeaders, String>> createDefaultReplacementDefinitions() {
        final Map<String, Function<DittoHeaders, String>> defaultReplacementDefinitions = new LinkedHashMap<>();
        defaultReplacementDefinitions.put(SubjectIdReplacementDefinition.REPLACER_NAME,
                SubjectIdReplacementDefinition.getInstance());
        return Collections.unmodifiableMap(defaultReplacementDefinitions);
    }

    private static PlaceholderSubstitution createInstance(
            final Map<String, Function<DittoHeaders, String>> replacementDefinitions) {
        final HeaderBasedPlaceholderSubstitutionAlgorithm algorithm =
                HeaderBasedPlaceholderSubstitutionAlgorithm.newInstance(replacementDefinitions);
        final SubstitutionStrategyRegistry substitutionStrategyRegistry =
                SubstitutionStrategyRegistry.newInstance();

        return new PlaceholderSubstitution(algorithm, substitutionStrategyRegistry);
    }
}
