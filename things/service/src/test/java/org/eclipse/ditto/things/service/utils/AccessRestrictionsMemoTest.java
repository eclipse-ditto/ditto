/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.Test;

/**
 * Unit tests for {@link AccessRestrictionsMemo}.
 */
public final class AccessRestrictionsMemoTest {

    private static final Policy POLICY = Policy.newBuilder(PolicyId.of("org.eclipse.ditto.test", "policy")).build();

    private final AtomicInteger calculations = new AtomicInteger();

    @Test
    public void reusesResultForSameEnforcerInstanceStructureAndPartialFlag() {
        final AccessRestrictionsMemo underTest = new AccessRestrictionsMemo(2);
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(POLICY);

        final AccessRestrictions first = underTest.get(policyEnforcer, 1L, true, calculation());
        final AccessRestrictions second = underTest.get(policyEnforcer, 1L, true, calculation());

        assertThat(second).isSameAs(first);
        assertThat(calculations).hasValue(1);
    }

    @Test
    public void recalculatesForOtherEnforcerInstanceStructureOrPartialFlag() {
        final AccessRestrictionsMemo underTest = new AccessRestrictionsMemo(8);
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(POLICY);

        underTest.get(policyEnforcer, 1L, true, calculation());
        underTest.get(PolicyEnforcer.of(POLICY), 1L, true, calculation());
        underTest.get(policyEnforcer, 2L, true, calculation());
        underTest.get(policyEnforcer, 1L, false, calculation());

        assertThat(calculations).hasValue(4);
    }

    @Test
    public void evictsLeastRecentlyUsedEntry() {
        final AccessRestrictionsMemo underTest = new AccessRestrictionsMemo(1);
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(POLICY);

        underTest.get(policyEnforcer, 1L, true, calculation());
        underTest.get(policyEnforcer, 2L, true, calculation());
        underTest.get(policyEnforcer, 1L, true, calculation());

        assertThat(calculations).hasValue(3);
    }

    @Test
    public void dropsEntriesOfReplacedEnforcer() {
        final AccessRestrictionsMemo underTest = new AccessRestrictionsMemo(8);
        final PolicyEnforcer replaced = PolicyEnforcer.of(POLICY);

        underTest.get(replaced, 1L, true, calculation());
        underTest.get(PolicyEnforcer.of(POLICY), 1L, true, calculation());
        underTest.get(replaced, 1L, true, calculation());

        assertThat(calculations).hasValue(3);
    }

    private Supplier<AccessRestrictions> calculation() {
        return () -> new AccessRestrictions(null, null, Set.of("subject-" + calculations.incrementAndGet()));
    }

}
