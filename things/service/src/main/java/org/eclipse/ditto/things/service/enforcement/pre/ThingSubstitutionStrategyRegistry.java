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
package org.eclipse.ditto.things.service.enforcement.pre;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.policies.enforcement.placeholders.strategies.SubstitutionStrategy;
import org.eclipse.ditto.policies.enforcement.placeholders.strategies.SubstitutionStrategyRegistry;

/**
 * Registry containing all of the thing specific instances of {@link SubstitutionStrategy}.
 */
@Immutable
final class ThingSubstitutionStrategyRegistry implements SubstitutionStrategyRegistry {

    private final List<SubstitutionStrategy<?>> strategies;

    private ThingSubstitutionStrategyRegistry() {
        strategies = List.copyOf(createStrategies());
    }

    public static ThingSubstitutionStrategyRegistry newInstance() {
        return new ThingSubstitutionStrategyRegistry();
    }

    /**
     * Get a matching strategy for handling the given {@code withDittoHeaders}.
     *
     * @param withDittoHeaders the instance of {@link org.eclipse.ditto.base.model.headers.WithDittoHeaders} to be handled.
     * @return an {@link java.util.Optional} containing the first strategy which matches; an empty {@link java.util.Optional} in case no
     * strategy matches.
     */
    @SuppressWarnings({"rawtypes", "java:S3740"})
    @Override
    public Optional<SubstitutionStrategy> getMatchingStrategy(final DittoHeadersSettable<?> withDittoHeaders) {
        for (final SubstitutionStrategy<?> strategy : strategies) {
            if (strategy.matches(withDittoHeaders)) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<SubstitutionStrategy<?>> getStrategies() {
        return strategies;
    }

    private static List<SubstitutionStrategy<?>> createStrategies() {
        final List<SubstitutionStrategy<?>> strategies = new LinkedList<>();

        // replacement for policy-subject-id
        strategies.add(new CreateThingSubstitutionStrategy());

        return strategies;
    }
}
