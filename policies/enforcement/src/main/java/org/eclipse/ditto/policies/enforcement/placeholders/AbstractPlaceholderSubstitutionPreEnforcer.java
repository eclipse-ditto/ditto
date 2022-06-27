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
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.policies.enforcement.placeholders.strategies.SubstitutionStrategy;
import org.eclipse.ditto.policies.enforcement.placeholders.strategies.SubstitutionStrategyRegistry;
import org.eclipse.ditto.policies.enforcement.pre.PreEnforcer;

/**
 * Abstract Pre-Enforcer which applies substitution of placeholders on a command (subtype of {@link Signal}) based on
 * its {@link DittoHeaders}.
 */
@Immutable
public abstract class AbstractPlaceholderSubstitutionPreEnforcer implements PreEnforcer {

    private final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm;
    private final SubstitutionStrategyRegistry substitutionStrategyRegistry;

    protected AbstractPlaceholderSubstitutionPreEnforcer(final HeaderBasedPlaceholderSubstitutionAlgorithm substitutionAlgorithm,
            final SubstitutionStrategyRegistry substitutionStrategyRegistry) {

        this.substitutionAlgorithm = substitutionAlgorithm;
        this.substitutionStrategyRegistry = substitutionStrategyRegistry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        requireNonNull(signal);

        final Optional<SubstitutionStrategy<? extends Signal<?>>> firstMatchingStrategyOpt =
                substitutionStrategyRegistry.getMatchingStrategy(signal);
        if (firstMatchingStrategyOpt.isPresent()) {
            final SubstitutionStrategy<Signal<?>> firstMatchingStrategy =
                    (SubstitutionStrategy<Signal<?>>) firstMatchingStrategyOpt.get();
            return CompletableFuture.supplyAsync(() ->
                    firstMatchingStrategy.apply(signal, substitutionAlgorithm)
            ).thenApply(Function.identity());
        } else {
            return CompletableFuture.completedFuture(signal);
        }
    }

    protected static Map<String, Function<DittoHeaders, String>> createDefaultReplacementDefinitions() {
        final Map<String, Function<DittoHeaders, String>> defaultReplacementDefinitions = new LinkedHashMap<>();
        defaultReplacementDefinitions.put(SubjectIdReplacementDefinition.REPLACER_NAME,
                SubjectIdReplacementDefinition.getInstance());
        defaultReplacementDefinitions.put(SubjectIdReplacementDefinition.LEGACY_REPLACER_NAME,
                SubjectIdReplacementDefinition.getInstance());
        return Collections.unmodifiableMap(defaultReplacementDefinitions);
    }
}
