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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.FeatureCreated;
import org.junit.Test;

/**
 * Unit test for {@link FeatureCreatedStrategy}.
 */
public final class FeatureCreatedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(FeatureCreatedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final FeatureCreatedStrategy strategy = new FeatureCreatedStrategy();
        final FeatureCreated event = FeatureCreated.of(THING_ID, FEATURE, REVISION, TIMESTAMP, DittoHeaders.empty(),
                null);

        final Thing thingWithEventApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setFeature(FEATURE)
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
