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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.events.things.ThingMerged;
import org.junit.Test;

/**
 * Unit test for {@link ThingMergedStrategy}.
 */
public final class ThingMergedStrategyTest extends AbstractStrategyTest {

    private static final Instant TIMESTAMP = Instant.now();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingMergedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final ThingMergedStrategy strategy = new ThingMergedStrategy();
        final ThingMerged event = ThingMerged.of(THING.getEntityId().orElseThrow(),
                TestConstants.Thing.ABSOLUTE_LOCATION_ATTRIBUTE_POINTER,
                TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE, REVISION, TIMESTAMP, DittoHeaders.empty(), null);

        final Thing thingWithMergeApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Thing expected = ThingsModelFactory.newThingBuilder()
                .setAttribute(TestConstants.Thing.LOCATION_ATTRIBUTE_POINTER,
                        TestConstants.Thing.LOCATION_ATTRIBUTE_VALUE)
                .setId(THING_ID)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .build();
        assertThat(thingWithMergeApplied).isEqualTo(expected);
    }

}
