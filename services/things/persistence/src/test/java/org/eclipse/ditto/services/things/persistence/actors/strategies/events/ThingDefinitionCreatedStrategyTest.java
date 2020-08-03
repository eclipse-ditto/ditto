/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.signals.events.things.ThingDefinitionCreated;
import org.junit.Test;

/**
 * Unit test for
 * {@link org.eclipse.ditto.services.things.persistence.actors.strategies.events.ThingDefinitionCreatedStrategy}.
 */
public final class ThingDefinitionCreatedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingDefinitionCreatedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final ThingDefinitionCreatedStrategy strategy = new ThingDefinitionCreatedStrategy();
        final ThingDefinitionCreated event = ThingDefinitionCreated.of(THING_ID, THING_DEFINITION, REVISION,
                DittoHeaders.empty());

        final Thing thingWithEventApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setDefinition(THING_DEFINITION)
                .setRevision(NEXT_REVISION)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}