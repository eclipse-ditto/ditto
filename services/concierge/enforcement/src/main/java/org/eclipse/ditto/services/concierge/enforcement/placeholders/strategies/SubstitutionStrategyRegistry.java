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
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

/**
 * Registry containing all of the required instances of {@link SubstitutionStrategy}.
 */
@Immutable
public final class SubstitutionStrategyRegistry {

    private final List<SubstitutionStrategy> strategies;

    private SubstitutionStrategyRegistry() {
        strategies = Collections.unmodifiableList(new ArrayList<>(createStrategies()));
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
    public final Optional<SubstitutionStrategy> getMatchingStrategy(final WithDittoHeaders withDittoHeaders) {
        for (final SubstitutionStrategy strategy : strategies) {
            if (strategy.matches(withDittoHeaders)) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }

    // for testing purposes
    List<SubstitutionStrategy> getStrategies() {
        return strategies;
    }

    private static List<SubstitutionStrategy> createStrategies() {
        final List<SubstitutionStrategy> strategies = new LinkedList<>();

        // replacement for policy-subject-id
        strategies.add(new ModifySubjectSubstitutionStrategy());
        strategies.add(new ModifySubjectsSubstitutionStrategy());
        strategies.add(new ModifyPolicyEntrySubstitutionStrategy());
        strategies.add(new ModifyPolicyEntriesSubstitutionStrategy());
        strategies.add(new ModifyPolicySubstitutionStrategy());
        strategies.add(new CreatePolicySubstitutionStrategy());

        // replacement for acl-subject-id
        strategies.add(new ModifyAclEntrySubstitutionStrategy());
        strategies.add(new ModifyAclSubstitutionStrategy());

        // replacement for both policy-subject-id and acl-subject-id
        strategies.add(new ModifyThingSubstitutionStrategy());
        strategies.add(new CreateThingSubstitutionStrategy());

        return strategies;
    }
}
