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

import java.time.Instant;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertyDeleted;
import org.junit.Test;

/**
 * Unit test for {@link FeatureDesiredPropertyDeletedStrategy}.
 */
public final class FeatureDesiredPropertyDeletedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(FeatureDesiredPropertyDeletedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final Instant timestamp = Instant.now();
        final FeatureDesiredPropertyDeletedStrategy strategy = new FeatureDesiredPropertyDeletedStrategy();
        final FeatureDesiredPropertyDeleted event =
                FeatureDesiredPropertyDeleted.of(THING_ID, FEATURE_ID, FEATURE_DESIRED_PROPERTY_POINTER,
                        REVISION, timestamp, DittoHeaders.empty(), null);

        final Thing thingWithFeatureWithDesiredProperty = THING.toBuilder()
                .setFeature(FEATURE.toBuilder()
                        .desiredProperties(FeatureProperties.newBuilder()
                                .set(FEATURE_DESIRED_PROPERTY_POINTER, FEATURE_DESIRED_PROPERTY_VALUE)
                                .build())
                        .build())
                .build();
        final Thing thingWithEventApplied = strategy.handle(event, thingWithFeatureWithDesiredProperty, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setFeatureDesiredProperties(FEATURE_ID, FeatureProperties.newBuilder().build())
                .setRevision(NEXT_REVISION)
                .setModified(timestamp)
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

    @Test
    public void replacesMetadataAtResourcePath() {
        final Metadata eventMetadata = Metadata.newMetadata(JsonObject.of("{\"coloring\":[\"E104\",\"E129\"]}"));
        final Instant timestamp = Instant.now();
        final FeatureDesiredPropertyDeletedStrategy strategy = new FeatureDesiredPropertyDeletedStrategy();
        final FeatureDesiredPropertyDeleted event =
                FeatureDesiredPropertyDeleted.of(THING_ID, FEATURE_ID, FEATURE_DESIRED_PROPERTY_POINTER,
                        REVISION, timestamp, DittoHeaders.empty(), eventMetadata);

        final Metadata thingMetadata = Metadata.newBuilder()
                .set("attributes/preservatives", JsonObject.of("{\"count\":53}"))
                .build();
        final Thing thingWithFeatureWithDesiredProperty = THING.toBuilder()
                .setFeature(FEATURE.toBuilder()
                        .desiredProperties(FeatureProperties.newBuilder()
                                .set(FEATURE_DESIRED_PROPERTY_POINTER, FEATURE_DESIRED_PROPERTY_VALUE)
                                .build())
                        .build())
                .setMetadata(thingMetadata)
                .build();
        final Thing thingWithEventApplied = strategy.handle(event, thingWithFeatureWithDesiredProperty, NEXT_REVISION);

        final Metadata expectedMetadata = thingMetadata.toBuilder()
                .set(JsonPointer.of(String.format("features/%s/desiredProperties", FEATURE_ID)),
                        JsonFactory.newObject())
                .build();
        final Thing expected = THING.toBuilder()
                .setFeatureDesiredProperties(FEATURE_ID, FeatureProperties.newBuilder().build())
                .setRevision(NEXT_REVISION)
                .setMetadata(METADATA)
                .setModified(timestamp)
                .setMetadata(expectedMetadata)
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
