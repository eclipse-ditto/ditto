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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.services.things.persistence.actors.strategies.events.FeatureDefinitionDeletedStrategy}.
 */
public final class FeatureDefinitionDeletedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(FeatureDefinitionDeletedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final FeatureDefinitionDeletedStrategy strategy = new FeatureDefinitionDeletedStrategy();
        final FeatureDefinitionDeleted event =
                FeatureDefinitionDeleted.of(THING_ID, FEATURE_ID, REVISION, DittoHeaders.empty());

        final Thing thingWithFeatureWithDefinition = THING.toBuilder()
                .setFeature(FEATURE.toBuilder()
                        .definition(FEATURE_DEFINITION)
                        .build())
                .build();
        final Thing thingWithEventApplied = strategy.handle(event, thingWithFeatureWithDefinition,
                NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setFeature(FEATURE)
                .setRevision(NEXT_REVISION)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
