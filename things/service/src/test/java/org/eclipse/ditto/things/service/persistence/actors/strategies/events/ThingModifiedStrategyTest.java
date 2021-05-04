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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.junit.Test;

/**
 * Unit test for {@link ThingModifiedStrategy}.
 */
public final class ThingModifiedStrategyTest extends AbstractStrategyTest {

    private static final Instant TIMESTAMP = Instant.now();
    private static final Metadata METADATA = Metadata.newBuilder().set("hello", "world").build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingModifiedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final ThingModifiedStrategy strategy = new ThingModifiedStrategy();
        final ThingModified event = ThingModified.of(THING, REVISION, TIMESTAMP, DittoHeaders.empty(), METADATA);

        final Thing thingWithEventApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(NEXT_REVISION)
                .setMetadata(METADATA)
                .setModified(TIMESTAMP)
                .setMetadata(METADATA)
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

    @Test
    public void replacesPreviousMetadata() {
        final ThingModifiedStrategy strategy = new ThingModifiedStrategy();
        final ThingModified event = ThingModified.of(THING, REVISION, TIMESTAMP, DittoHeaders.empty(), METADATA);

        final Metadata previousMetadata = Metadata.newBuilder().set("additives", JsonArray.of("[\"E129\"]")).build();
        final Thing thingWithEventApplied =
                strategy.handle(event, THING.toBuilder().setMetadata(previousMetadata).build(), NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(NEXT_REVISION)
                .setMetadata(METADATA)
                .setModified(TIMESTAMP)
                .setMetadata(METADATA)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
