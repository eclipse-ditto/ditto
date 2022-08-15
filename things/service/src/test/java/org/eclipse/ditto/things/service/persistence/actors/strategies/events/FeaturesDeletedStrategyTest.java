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
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.FeaturesDeleted;
import org.junit.Test;

/**
 * Unit test for {@link FeaturesDeletedStrategy}.
 */
public final class FeaturesDeletedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(FeaturesDeletedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final FeaturesDeletedStrategy strategy = new FeaturesDeletedStrategy();
        final FeaturesDeleted event = FeaturesDeleted.of(THING_ID, REVISION, TIMESTAMP, DittoHeaders.empty(), null);

        final Metadata thingMetadata = Metadata.newBuilder()
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
        final Thing thingWithFeatures = THING.toBuilder()
                .setFeatures(FEATURES)
                .setMetadata(thingMetadata)
                .build();
        final Thing thingWithEventApplied = strategy.handle(event, thingWithFeatures, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .setMetadata(thingMetadata.toBuilder().remove("features").build())
                .build();

        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
