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
package org.eclipse.ditto.things.service.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Tests {@link ThingsRootActor#selectMostRecent(Set)}, which guards against an outdated WoT validation config -
 * e.g. one written by a node which has meanwhile left the cluster - shadowing the current one.
 */
public final class ThingsRootActorWotValidationConfigSelectionTest {

    private static JsonObject config(final long revision) {
        return JsonObject.newBuilder()
                .set("configId", "ditto:global")
                .set("_revision", revision)
                .build();
    }

    private static Set<JsonObject> setOf(final JsonObject... configs) {
        return new LinkedHashSet<>(java.util.Arrays.asList(configs));
    }

    @Test
    public void emptySetSelectsNothing() {
        assertThat(ThingsRootActor.selectMostRecent(Set.of())).isEmpty();
    }

    @Test
    public void singleElementIsSelected() {
        assertThat(ThingsRootActor.selectMostRecent(setOf(config(503)))).contains(config(503));
    }

    @Test
    public void highestRevisionWinsRegardlessOfIterationOrder() {
        assertThat(ThingsRootActor.selectMostRecent(setOf(config(503), config(530)))).contains(config(530));
        assertThat(ThingsRootActor.selectMostRecent(setOf(config(530), config(503)))).contains(config(530));
    }

    @Test
    public void configWithoutRevisionLosesAgainstOneWithRevision() {
        final JsonObject withoutRevision = JsonObject.newBuilder().set("configId", "ditto:global").build();

        assertThat(ThingsRootActor.selectMostRecent(setOf(withoutRevision, config(0)))).contains(config(0));
    }

    @Test
    public void configWithoutRevisionIsStillSelectedWhenItIsTheOnlyOne() {
        final JsonObject withoutRevision = JsonObject.newBuilder().set("configId", "ditto:global").build();

        assertThat(ThingsRootActor.selectMostRecent(setOf(withoutRevision))).contains(withoutRevision);
    }
}
