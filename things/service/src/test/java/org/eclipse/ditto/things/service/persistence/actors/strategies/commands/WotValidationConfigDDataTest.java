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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;

import org.apache.pekko.actor.Address;
import org.apache.pekko.cluster.UniqueAddress;
import org.apache.pekko.cluster.ddata.ORSet;
import org.apache.pekko.cluster.ddata.SelfUniqueAddress;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Tests the {@code ORSet} update functions of {@link WotValidationConfigDData}.
 * <p>
 * The functions are exercised the way Pekko's {@code Replicator} uses them: the returned value is merged into the
 * locally stored one rather than replacing it. A function which is not derived from the {@code ORSet} it is handed
 * can therefore only supersede the element written by its own node, leaving elements written by other nodes -
 * e.g. a node which has meanwhile left the cluster - in the set forever.
 */
public final class WotValidationConfigDDataTest {

    private static final SelfUniqueAddress NODE_A = selfUniqueAddress("node-a", 1L);
    private static final SelfUniqueAddress NODE_B = selfUniqueAddress("node-b", 2L);

    private static SelfUniqueAddress selfUniqueAddress(final String host, final long uid) {
        return new SelfUniqueAddress(new UniqueAddress(new Address("pekko", "ditto", host, 2551), uid));
    }

    private static JsonObject config(final long revision) {
        return JsonObject.newBuilder()
                .set("configId", "ditto:global")
                .set("_revision", revision)
                .build();
    }

    private static long revisionOf(final JsonObject config) {
        return config.getValue("_revision").orElseThrow().asLong();
    }

    /** The update function of {@code add()}. */
    private static Function<ORSet<JsonObject>, ORSet<JsonObject>> add(final SelfUniqueAddress node,
            final JsonObject config) {
        return orSet -> removeAll(orSet, node).add(node, config);
    }

    /** The update function of {@code clear()}. */
    private static Function<ORSet<JsonObject>, ORSet<JsonObject>> clear(final SelfUniqueAddress node) {
        return orSet -> removeAll(orSet, node);
    }

    private static ORSet<JsonObject> removeAll(final ORSet<JsonObject> orSet, final SelfUniqueAddress node) {
        ORSet<JsonObject> result = orSet;
        for (final JsonObject existing : orSet.getElements()) {
            result = result.remove(node, existing);
        }
        return result;
    }

    /** Applies an update function the way the {@code Replicator} does: merge the result into the local value. */
    private static ORSet<JsonObject> replicate(final ORSet<JsonObject> local,
            final Function<ORSet<JsonObject>, ORSet<JsonObject>> updateFunction) {
        return local.merge(updateFunction.apply(local));
    }

    @Test
    public void repeatedAddsFromTheSameNodeKeepASingleElement() {
        ORSet<JsonObject> stored = replicate(ORSet.empty(), add(NODE_A, config(1)));
        stored = replicate(stored, add(NODE_A, config(2)));
        stored = replicate(stored, add(NODE_A, config(3)));

        assertThat(stored.getElements()).containsExactly(config(3));
    }

    @Test
    public void addsFromDifferentNodesKeepASingleElement() {
        ORSet<JsonObject> stored = replicate(ORSet.empty(), add(NODE_A, config(1)));
        stored = replicate(stored, add(NODE_B, config(2)));
        stored = replicate(stored, add(NODE_A, config(3)));

        assertThat(stored.getElements()).containsExactly(config(3));
    }

    @Test
    public void addRecoversFromASetContainingAnElementOfADepartedNode() {
        // a set as it is left behind by an update function which is not derived from the current value:
        // node A rolled away but its element stays, shadowing everything node B writes afterwards
        final ORSet<JsonObject> corrupted = ORSet.<JsonObject>empty().add(NODE_A, config(503))
                .merge(ORSet.<JsonObject>empty().add(NODE_B, config(529)));
        assertThat(corrupted.getElements()).hasSize(2);

        final ORSet<JsonObject> repaired = replicate(corrupted, add(NODE_B, config(530)));

        assertThat(repaired.getElements()).containsExactly(config(530));
    }

    @Test
    public void clearRemovesElementsOfAllNodes() {
        final ORSet<JsonObject> stored = ORSet.<JsonObject>empty().add(NODE_A, config(1))
                .merge(ORSet.<JsonObject>empty().add(NODE_B, config(2)));

        final ORSet<JsonObject> cleared = replicate(stored, clear(NODE_B));

        assertThat(cleared.getElements()).isEmpty();
    }

    @Test
    public void selectMostRecentPicksTheHighestRevision() {
        final ORSet<JsonObject> stored = ORSet.<JsonObject>empty().add(NODE_A, config(503))
                .merge(ORSet.<JsonObject>empty().add(NODE_B, config(529)));

        assertThat(stored.getElements()).hasSize(2);
        assertThat(revisionOf(stored.getElements().iterator().next())).isEqualTo(503L);
    }
}
