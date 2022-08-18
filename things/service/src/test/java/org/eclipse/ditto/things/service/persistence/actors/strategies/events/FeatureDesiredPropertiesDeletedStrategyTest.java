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
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesDeleted;
import org.junit.Test;

/**
 * Unit test for {@link FeatureDesiredPropertiesDeletedStrategy}.
 */
public final class FeatureDesiredPropertiesDeletedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(FeatureDesiredPropertiesDeletedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final Instant timestamp = Instant.now();
        final FeatureDesiredPropertiesDeletedStrategy strategy = new FeatureDesiredPropertiesDeletedStrategy();
        final FeatureDesiredPropertiesDeleted event = FeatureDesiredPropertiesDeleted.of(THING_ID, FEATURE_ID, REVISION,
                timestamp, DittoHeaders.empty(), null);

        final Thing thingWithFeatureWithDesiredProperties = THING.toBuilder()
                .setFeature(FEATURE.toBuilder()
                        .desiredProperties(FEATURE_DESIRED_PROPERTIES)
                        .build())
                .build();
        final Thing thingWithEventApplied = strategy.handle(event, thingWithFeatureWithDesiredProperties,
                NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setFeature(FEATURE)
                .setRevision(NEXT_REVISION)
                .setModified(timestamp)
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

    @Test
    public void doesNotOverrideExistingMetadata() {
        final Instant timestamp = Instant.now();
        final FeatureDesiredPropertiesDeletedStrategy strategy = new FeatureDesiredPropertiesDeletedStrategy();
        final FeatureDesiredPropertiesDeleted event = FeatureDesiredPropertiesDeleted.of(THING_ID, FEATURE_ID, REVISION,
                timestamp, DittoHeaders.empty(), null);

        final Metadata existingMetadata = Metadata.newMetadata(JsonObject.newBuilder()
                .set(JsonPointer.of(String.format("features/%s", FEATURE_ID)), JsonObject.empty())
                .set(JsonPointer.of("attributes/additives"), JsonObject.of("{\"E104\":true}"))
                .build());
        final Thing thingWithFeatureWithDesiredProperties = THING.toBuilder()
                .setFeature(FEATURE.toBuilder()
                        .desiredProperties(FEATURE_DESIRED_PROPERTIES)
                        .build())
                .setMetadata(existingMetadata)
                .build();
        final Thing thingWithEventApplied = strategy.handle(event, thingWithFeatureWithDesiredProperties,
                NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setFeature(FEATURE)
                .setRevision(NEXT_REVISION)
                .setMetadata(existingMetadata)
                .setModified(timestamp)
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
