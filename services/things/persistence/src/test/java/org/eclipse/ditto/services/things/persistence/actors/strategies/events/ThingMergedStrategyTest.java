/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.signals.events.things.ThingMerged;
import org.junit.Test;

/**
 * Unit test for {@link ThingMergedStrategy}.
 */
public final class ThingMergedStrategyTest extends AbstractStrategyTest {

    private static final Instant TIMESTAMP = Instant.now();
    private static final Metadata METADATA = Metadata.newBuilder().set("hello", "world").build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingMergedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final ThingMergedStrategy strategy = new ThingMergedStrategy();
        final ThingMerged event = ThingMerged.of(THING.getEntityId().orElseThrow(),
                JsonPointer.empty(), JsonValue.nullLiteral(), REVISION, TIMESTAMP, DittoHeaders.empty(), METADATA);

        final Thing thingWithEventApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .setMetadata(METADATA)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

    @Test
    public void replacesPreviousMetadata() {
        final ThingMergedStrategy strategy = new ThingMergedStrategy();
        final ThingMerged event = ThingMerged.of(THING.getEntityId().orElseThrow(),
                JsonPointer.empty(), JsonValue.nullLiteral(), REVISION, TIMESTAMP, DittoHeaders.empty(), METADATA);

        final Metadata previousMetadata = Metadata.newBuilder().set("additives", JsonArray.of("[\"E129\"]")).build();
        final Thing thingWithEventApplied =
                strategy.handle(event, THING.toBuilder().setMetadata(previousMetadata).build(), NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .setMetadata(METADATA)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
