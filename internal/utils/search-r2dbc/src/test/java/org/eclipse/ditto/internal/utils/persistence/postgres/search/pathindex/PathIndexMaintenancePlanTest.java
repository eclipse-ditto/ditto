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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.PathIndexMaintenance.ExistingIndex;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.PathIndexMaintenance.Plan;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.Test;

/**
 * Unit tests for {@link PathIndexMaintenance#plan(List, List, List)} (what a run drops, repairs and builds for a given
 * configuration and catalog state, system indexes included) and {@link PathIndexMaintenance#isOneSession} (the "not
 * one server session" decision).
 */
public final class PathIndexMaintenancePlanTest {

    private static final ThingsFieldExpressionFactory FEF = ThingsFieldExpressionFactory.of(Map.of());
    private static final PathIndex PROP0 = PathIndex.of("features/*/properties/prop0", PathIndexKind.TRIGRAM, FEF);
    private static final PathIndex CITY = PathIndex.of("attributes/location/city", PathIndexKind.TRIGRAM, FEF);
    private static final SystemSearchIndex MULTI = SystemSearchIndex.MULTI_VALUED;

    @Test
    public void nothingConfiguredAndNothingThereIsEmpty() {
        assertThat(PathIndexMaintenance.plan(List.of(), List.of()).isEmpty()).isTrue();
    }

    @Test
    public void validConfiguredIndexesAreUpToDate() {
        final Plan plan = PathIndexMaintenance.plan(List.of(PROP0, CITY),
                List.of(valid(PROP0.name()), valid(CITY.name())));

        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    public void missingConfiguredIndexIsBuilt() {
        final Plan plan = PathIndexMaintenance.plan(List.of(PROP0, CITY), List.of(valid(PROP0.name())));

        assertThat(plan.build()).containsExactly(CITY);
        assertThat(plan.dropInvalid()).isEmpty();
        assertThat(plan.dropUnconfigured()).isEmpty();
    }

    @Test
    public void invalidConfiguredIndexIsDroppedAndRebuilt() {
        final Plan plan = PathIndexMaintenance.plan(List.of(PROP0), List.of(invalid(PROP0.name())));

        assertThat(plan.dropInvalid()).containsExactly(PROP0.name());
        assertThat(plan.build()).containsExactly(PROP0);
        assertThat(plan.dropUnconfigured()).isEmpty();
    }

    @Test
    public void invalidUnconfiguredIndexIsDroppedOnly() {
        final Plan plan = PathIndexMaintenance.plan(List.of(), List.of(invalid(CITY.name())));

        assertThat(plan.dropInvalid()).containsExactly(CITY.name());
        assertThat(plan.build()).isEmpty();
        assertThat(plan.dropUnconfigured()).isEmpty();
    }

    @Test
    public void validUnconfiguredIndexIsDropped() {
        final Plan plan = PathIndexMaintenance.plan(List.of(PROP0),
                List.of(valid(PROP0.name()), valid(CITY.name()), valid("sf_path_trgm_0000000000000000")));

        assertThat(plan.dropUnconfigured()).containsExactly(CITY.name(), "sf_path_trgm_0000000000000000");
        assertThat(plan.build()).isEmpty();
        assertThat(plan.dropInvalid()).isEmpty();
    }

    @Test
    public void buildsFollowTheConfigurationOrder() {
        final Plan plan = PathIndexMaintenance.plan(List.of(CITY, PROP0), List.of());

        assertThat(plan.build()).containsExactly(CITY, PROP0);
    }

    @Test
    public void aMissingSystemIndexIsBuiltEvenWithoutAConfiguredList() {
        final Plan plan = PathIndexMaintenance.plan(List.of(MULTI), null, List.of(valid(PROP0.name())));

        assertThat(plan.buildSystem()).containsExactly(MULTI);
        assertThat(plan.dropInvalid()).isEmpty();
        assertThat(plan.dropUnconfigured()).as("an unset list manages no per-path index").isEmpty();
        assertThat(plan.build()).isEmpty();
    }

    @Test
    public void anInvalidSystemIndexIsDroppedAndRebuilt() {
        final Plan plan = PathIndexMaintenance.plan(List.of(MULTI), List.of(), List.of(invalid(MULTI.indexName())));

        assertThat(plan.dropInvalid()).containsExactly(MULTI.indexName());
        assertThat(plan.buildSystem()).containsExactly(MULTI);
    }

    @Test
    public void aValidSystemIndexIsNeverDroppedAsUnconfigured() {
        final Plan plan = PathIndexMaintenance.plan(List.of(MULTI), List.of(), List.of(valid(MULTI.indexName())));

        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    public void aSystemIndexThisRunDoesNotManageIsLeftAlone() {
        // e.g. the test factory without system indexes, on a database that has sf_multi
        final Plan plan = PathIndexMaintenance.plan(List.of(), List.of(), List.of(valid(MULTI.indexName())));

        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    public void oneSessionOnlyWhenTheCheckingBackendIsTheLockingOneAndHoldsTheLock() {
        assertThat(PathIndexMaintenance.isOneSession(42, 42, true)).isTrue();
        // a transaction pooler ran the check on another server connection
        assertThat(PathIndexMaintenance.isOneSession(42, 43, false)).isFalse();
        assertThat(PathIndexMaintenance.isOneSession(42, 43, true)).isFalse();
        // same backend pid but the lock is not held there (e.g. it was released or reassigned)
        assertThat(PathIndexMaintenance.isOneSession(42, 42, false)).isFalse();
        assertThat(PathIndexMaintenance.isOneSession(42, null, true)).isFalse();
        assertThat(PathIndexMaintenance.isOneSession(42, 42, null)).isFalse();
    }

    private static ExistingIndex valid(final String name) {
        return new ExistingIndex(name, true);
    }

    private static ExistingIndex invalid(final String name) {
        return new ExistingIndex(name, false);
    }

}
