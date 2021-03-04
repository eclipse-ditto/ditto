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
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.services.things.persistence.actors.strategies.events.AttributeModifiedStrategy}.
 */
public final class AttributeModifiedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(AttributeModifiedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final AttributeModifiedStrategy strategy = new AttributeModifiedStrategy();
        final AttributeModified event = AttributeModified.of(THING_ID, ATTRIBUTE_POINTER, ATTRIBUTE_VALUE, REVISION,
                DittoHeaders.empty());

        final Thing thingWithEventApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setAttribute(ATTRIBUTE_POINTER, ATTRIBUTE_VALUE)
                .setRevision(NEXT_REVISION)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
