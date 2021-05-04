/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesCreated;
import org.junit.Test;

/**
 * Unit test for {@link FeatureDesiredPropertiesCreatedStrategy}.
 */
public final class FeatureDesiredPropertiesCreatedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(FeatureDesiredPropertiesCreatedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final FeatureDesiredPropertiesCreatedStrategy strategy = new FeatureDesiredPropertiesCreatedStrategy();
        final FeatureDesiredPropertiesCreated event = FeatureDesiredPropertiesCreated.of(THING_ID, FEATURE_ID, FEATURE_DESIRED_PROPERTIES,
                REVISION, TIMESTAMP, DittoHeaders.empty(), null);

        final Thing thingWithEventApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setFeatureDesiredProperties(FEATURE_ID, FEATURE_DESIRED_PROPERTIES)
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
