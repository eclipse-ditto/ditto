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
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.AttributesDeleted;
import org.junit.Test;

/**
 * Unit test for {@link AttributesDeletedStrategy}.
 */
public final class AttributesDeletedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(AttributesDeletedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final AttributesDeletedStrategy strategy = new AttributesDeletedStrategy();
        final AttributesDeleted event = AttributesDeleted.of(THING_ID, REVISION, TIMESTAMP, DittoHeaders.empty(),
                null);

        final Metadata thingMetadata = Metadata.newBuilder()
                .set("attributes", JsonObject.newBuilder()
                        .set("bumlux", METADATA)
                        .build())
                .build();
        final Thing thingWithAttributes = THING.toBuilder()
                .setAttributes(ATTRIBUTES)
                .setMetadata(thingMetadata)
                .build();
        final Thing thingWithEventApplied = strategy.handle(event, thingWithAttributes, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .setMetadata(MetadataModelFactory.emptyMetadata())
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
