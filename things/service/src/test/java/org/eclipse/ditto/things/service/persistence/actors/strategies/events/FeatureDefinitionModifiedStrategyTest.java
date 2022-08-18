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
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionModified;
import org.junit.Test;

/**
 * Unit test for {@link FeatureDefinitionModifiedStrategy}.
 */
public final class FeatureDefinitionModifiedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(FeatureDefinitionModifiedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final FeatureDefinitionModifiedStrategy strategy = new FeatureDefinitionModifiedStrategy();
        final FeatureDefinitionModified event =
                FeatureDefinitionModified.of(THING_ID, FEATURE_ID, FEATURE_DEFINITION, REVISION, TIMESTAMP,
                        DittoHeaders.empty(), null);

        final Thing thingWithEventApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setFeatureDefinition(FEATURE_ID, FEATURE_DEFINITION)
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

    @Test
    public void appliesEventWithMetadataCorrectly() {
        final FeatureDefinitionModifiedStrategy strategy = new FeatureDefinitionModifiedStrategy();
        final FeatureDefinitionModified event =
                FeatureDefinitionModified.of(THING_ID, FEATURE_ID, FEATURE_DEFINITION, REVISION, TIMESTAMP,
                        DittoHeaders.empty(), METADATA);

        final Thing thingWithEventApplied = strategy.handle(event, THING, NEXT_REVISION);

        final Metadata expectedMetadata = Metadata.newBuilder()
                .set(Thing.JsonFields.FEATURES, JsonObject.newBuilder()
                        .set(FEATURE_ID, JsonObject.newBuilder()
                                .set(Feature.JsonFields.DEFINITION.getPointer(), METADATA)
                                .build())
                        .build())
                .build();

        final Thing expected = THING.toBuilder()
                .setFeatureDefinition(FEATURE_ID, FEATURE_DEFINITION)
                .setRevision(NEXT_REVISION)
                .setModified(TIMESTAMP)
                .setMetadata(expectedMetadata)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}
