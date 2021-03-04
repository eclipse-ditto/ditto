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
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.services.things.persistence.actors.strategies.events.AttributesDeletedStrategy}.
 */
public final class AttributesDeletedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(AttributesDeletedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final AttributesDeletedStrategy strategy = new AttributesDeletedStrategy();
        final AttributesDeleted event = AttributesDeleted.of(THING_ID, REVISION, DittoHeaders.empty());

        final Thing thingWithAttributes = THING.toBuilder().setAttributes(ATTRIBUTES).build();
        final Thing thingWithEventApplied = strategy.handle(event, thingWithAttributes, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setRevision(NEXT_REVISION)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
