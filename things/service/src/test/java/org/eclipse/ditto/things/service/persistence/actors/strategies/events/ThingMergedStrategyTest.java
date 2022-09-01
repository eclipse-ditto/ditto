/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.junit.Test;

/**
 * Unit test for {@link ThingMergedStrategy}.
 */
public final class ThingMergedStrategyTest extends AbstractStrategyTest {

    private static final Instant TIMESTAMP = Instant.now();
    private static final String METADATA_KEY = "description";
    private static final String METADATA_VALUE = "description of the location";
    private static final Metadata METADATA = Metadata.newBuilder().set(METADATA_KEY, METADATA_VALUE).build();
    private static final Metadata EXPECTED_METADATA = Metadata.newBuilder()
            .set(TestConstants.Thing.ABSOLUTE_LOCATION_ATTRIBUTE_POINTER.append(JsonPointer.of(METADATA_KEY)),
                    METADATA_VALUE)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingMergedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final ThingMergedStrategy strategy = new ThingMergedStrategy();
        final ThingMerged event = ThingMerged.of(THING.getEntityId().orElseThrow(),
                TestConstants.Thing.ABSOLUTE_LOCATION_ATTRIBUTE_POINTER,
                TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE, REVISION, TIMESTAMP, DittoHeaders.empty(), METADATA);

        final Thing thingWithMergeApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Thing expected = ThingsModelFactory.newThingBuilder()
                .setAttribute(TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER,
                        TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE)
                .setId(THING_ID)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(NEXT_REVISION)
                .setMetadata(METADATA)
                .setModified(TIMESTAMP)
                .setMetadata(EXPECTED_METADATA)
                .build();

        assertThat(thingWithMergeApplied).isEqualTo(expected);
    }

    @Test
    public void replacesPreviousMetadata() {
        final ThingMergedStrategy strategy = new ThingMergedStrategy();
        final ThingMerged event = ThingMerged.of(THING.getEntityId().orElseThrow(),
                TestConstants.Thing.ABSOLUTE_LOCATION_ATTRIBUTE_POINTER,
                TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE, REVISION, TIMESTAMP, DittoHeaders.empty(), METADATA);

        final Metadata previousMetadata = Metadata.newBuilder()
                .set(TestConstants.Thing.ABSOLUTE_LOCATION_ATTRIBUTE_POINTER.append(JsonPointer.of(METADATA_KEY)),
                        "description")
                .build();
        final Thing thingWithMergeApplied =
                strategy.handle(event, THING.toBuilder().setMetadata(previousMetadata).build(), NEXT_REVISION);

        final Thing expected = ThingsModelFactory.newThingBuilder()
                .setAttribute(TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER,
                        TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE)
                .setId(THING_ID)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(NEXT_REVISION)
                .setMetadata(METADATA)
                .setModified(TIMESTAMP)
                .setMetadata(EXPECTED_METADATA)
                .build();

        assertThat(thingWithMergeApplied).isEqualTo(expected);
    }

}
