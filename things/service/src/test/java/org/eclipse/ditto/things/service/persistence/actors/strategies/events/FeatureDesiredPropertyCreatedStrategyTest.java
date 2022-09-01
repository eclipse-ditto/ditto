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
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertyCreated;
import org.junit.Test;

/**
 * Unit test for {@link FeatureDesiredPropertyCreatedStrategy}.
 */
public final class FeatureDesiredPropertyCreatedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(FeatureDesiredPropertyCreatedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final FeatureDesiredPropertyCreatedStrategy strategy = new FeatureDesiredPropertyCreatedStrategy();
        final FeatureDesiredPropertyCreated event = FeatureDesiredPropertyCreated.of(THING_ID, FEATURE_ID, FEATURE_DESIRED_PROPERTY_POINTER,
                FEATURE_DESIRED_PROPERTY_VALUE, REVISION, TIMESTAMP, DittoHeaders.empty(), null);

        final Thing thingWithEventApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setFeatureDesiredProperty(FEATURE_ID, FEATURE_DESIRED_PROPERTY_POINTER, FEATURE_DESIRED_PROPERTY_VALUE)
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
