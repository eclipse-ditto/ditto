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
package org.eclipse.ditto.policies.enforcement.placeholders.strategies;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;

/**
 * Registry containing all of the required instances of {@link SubstitutionStrategy}.
 */
@Immutable
public final class SubstitutionStrategyRegistry {

    private final List<SubstitutionStrategy<?>> strategies;

    private SubstitutionStrategyRegistry() {
        strategies = List.copyOf(createStrategies());
    }

    public static SubstitutionStrategyRegistry newInstance() {
        return new SubstitutionStrategyRegistry();
    }

    /**
     * Get a matching strategy for handling the given {@code withDittoHeaders}.
     *
     * @param withDittoHeaders the instance of {@link WithDittoHeaders} to be handled.
     * @return an {@link Optional} containing the first strategy which matches; an empty {@link Optional} in case no
     * strategy matches.
     */
    @SuppressWarnings({"rawtypes", "java:S3740"})
    public Optional<SubstitutionStrategy> getMatchingStrategy(final DittoHeadersSettable<?> withDittoHeaders) {
        for (final SubstitutionStrategy<?> strategy : strategies) {
            if (strategy.matches(withDittoHeaders)) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }

    // for testing purposes
    List<SubstitutionStrategy<?>> getStrategies() {
        return strategies;
    }

    private static List<SubstitutionStrategy<?>> createStrategies() {
        final List<SubstitutionStrategy<?>> strategies = new LinkedList<>();

        // replacement for policy-subject-id
        strategies.add(new ModifySubjectSubstitutionStrategy());
        strategies.add(new ModifySubjectsSubstitutionStrategy());
        strategies.add(new ModifyPolicyEntrySubstitutionStrategy());
        strategies.add(new ModifyPolicyEntriesSubstitutionStrategy());
        strategies.add(new ModifyPolicySubstitutionStrategy());
        strategies.add(new CreatePolicySubstitutionStrategy());

        // replacement for both policy-subject-id
        strategies.add(new ModifyThingSubstitutionStrategy());
        strategies.add(new CreateThingSubstitutionStrategy());

        return strategies;
    }
}
