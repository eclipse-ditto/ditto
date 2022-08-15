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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionDeleted;
import org.junit.Test;

/**
 * Unit test for
 * {@link ThingDefinitionDeletedStrategy}.
 */
public final class ThingDefinitionDeletedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingDefinitionDeletedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final ThingDefinitionDeletedStrategy strategy = new ThingDefinitionDeletedStrategy();
        final ThingDefinitionDeleted event = ThingDefinitionDeleted.of(THING_ID, REVISION,
                TIMESTAMP, DittoHeaders.empty(), null);

        final Metadata thingMetadata = Metadata.newBuilder()
                .set("definition", METADATA)
                .set("features", JsonObject.newBuilder()
                        .set(FEATURE_ID, JsonObject.newBuilder()
                                .set("definition", JsonObject.newBuilder()
                                        .set(FEATURE_DEFINITION_ID, METADATA)
                                        .build())
                                .set("properties", JsonObject.newBuilder()
                                        .set(FEATURE_PROPERTY_POINTER, METADATA)
                                        .build())
                                .build())
                        .build())
                .build();
        final Thing thingWithDefinition = THING.toBuilder()
                .setDefinition(THING_DEFINITION)
                .setMetadata(thingMetadata)
                .build();
        final Thing thingWithEventApplied = strategy.handle(event, thingWithDefinition, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setDefinition(null)
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .setMetadata(thingMetadata.toBuilder().remove("definition").build())
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
