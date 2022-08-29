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

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
import org.junit.Test;

/**
 * Unit test for {@link AttributeDeletedStrategy}.
 */
public final class AttributeDeletedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(AttributeDeletedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final AttributeDeletedStrategy strategy = new AttributeDeletedStrategy();
        final AttributeDeleted event = AttributeDeleted.of(THING_ID, ATTRIBUTE_POINTER, REVISION, TIMESTAMP,
                DittoHeaders.empty(), null);

        final Metadata thingMetadata = Metadata.newBuilder()
                .set("attributes", JsonObject.newBuilder()
                        .set("bumlux", METADATA)
                        .build())
                .build();
        final Thing thingWithAttribute = THING.toBuilder()
                .setAttribute(ATTRIBUTE_POINTER, ATTRIBUTE_VALUE)
                .setMetadata(thingMetadata)
                .build();
        final Thing thingWithEventApplied = strategy.handle(event, thingWithAttribute, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setAttributes(Attributes.newBuilder().build())
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .setMetadata(thingMetadata.toBuilder().remove("attributes/bumlux").build())
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
