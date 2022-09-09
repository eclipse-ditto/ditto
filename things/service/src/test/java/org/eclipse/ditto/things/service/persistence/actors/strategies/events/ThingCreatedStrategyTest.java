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

import java.time.Instant;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.junit.Test;

/**
 * Unit test for {@link ThingCreatedStrategy}.
 */
public final class ThingCreatedStrategyTest extends AbstractStrategyTest {

    private static final Instant TIMESTAMP = Instant.now();
    private static final Metadata METADATA = Metadata.newBuilder().set("foo", "bar").build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingCreatedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final ThingCreatedStrategy strategy = new ThingCreatedStrategy();
        final ThingCreated event = ThingCreated.of(THING, REVISION, TIMESTAMP, DittoHeaders.empty(), METADATA);

        final Thing thingWithEventApplied = strategy.handle(event, null, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(NEXT_REVISION)
                .setMetadata(METADATA)
                .setModified(TIMESTAMP)
                .setCreated(TIMESTAMP)
                .setMetadata(METADATA)
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

    @Test
    public void appliesThingCreatedWithMetadata() {
        final ThingCreatedStrategy strategy = new ThingCreatedStrategy();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putMetadata(MetadataHeaderKey.of(JsonPointer.of("*/answer")), JsonValue.of(42))
                .build();

        final JsonObject metadataJson = JsonObject.newBuilder()
                .set("answer", 42)
                .build();

        final ThingCreated event = ThingCreated.of(THING, REVISION, TIMESTAMP, dittoHeaders, Metadata.newBuilder()
                .set(Thing.JsonFields.ID.getPointer(), metadataJson)
                .build());

        final Thing thingWithEventApplied = strategy.handle(event, null, NEXT_REVISION);


        final Thing expected = THING.toBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(NEXT_REVISION)
                .setMetadata(METADATA)
                .setModified(TIMESTAMP)
                .setCreated(TIMESTAMP)
                .setMetadata(Metadata.newBuilder()
                        .set(Thing.JsonFields.ID.getPointer(), metadataJson)
                        .build())
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
