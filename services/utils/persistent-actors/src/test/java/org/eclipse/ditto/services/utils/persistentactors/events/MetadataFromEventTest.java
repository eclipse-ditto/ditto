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
package org.eclipse.ditto.services.utils.persistentactors.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.Entity;
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link MetadataFromEvent}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MetadataFromEventTest {

    private static FeatureProperties fluxCapacitorProperties;
    private static Feature fluxCapacitor;
    private static Thing thingWithoutMetadata;

    @Mock private Event event;
    @Mock private Entity entity;

    @BeforeClass
    public static void setUpClass() {
        fluxCapacitorProperties = FeatureProperties.newBuilder()
                .set("capacity", JsonObject.newBuilder()
                        .set("value", 3.2)
                        .set("unit", "gallons per football")
                        .build())
                .build();
        fluxCapacitor = Feature.newBuilder()
                .properties(fluxCapacitorProperties)
                .withId("flux-capacitor")
                .build();
        thingWithoutMetadata = Thing.newBuilder()
                .setId(ThingId.generateRandom())
                .setAttribute(JsonPointer.of("productionYear"), JsonValue.of(2020))
                .setFeature(fluxCapacitor)
                .build();
    }

    @Before
    public void setUp() {
        Mockito.when(event.getImplementedSchemaVersion()).thenReturn(JsonSchemaVersion.LATEST);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(MetadataFromEvent.class,
                areImmutable(),
                provided(Event.class, Metadata.class).areAlsoImmutable());
    }

    @Test
    public void tryToGetInstanceWithNullEvent() {
        assertThatNullPointerException()
                .isThrownBy(() -> MetadataFromEvent.of(null, entity))
                .withMessage("The event must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullEntity() {
        assertThatNullPointerException()
                .isThrownBy(() -> MetadataFromEvent.of(event, null))
                .withMessage("The entity must not be null!")
                .withNoCause();
    }

    @Test
    public void getMetadataWhenEventHasNoEntityAndEntityHasNullExistingMetadata() {
        Mockito.when(event.getEntity(Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(entity.getMetadata()).thenReturn(Optional.empty());
        final MetadataFromEvent underTest = MetadataFromEvent.of(event, entity);

        assertThat(underTest.get()).isNull();
    }

    @Test
    public void entityHasNoMetadataAndEventDittoHeadersHaveNoMetadata() {
        Mockito.when(event.getEntity(Mockito.any())).thenReturn(Optional.of(thingWithoutMetadata.toJson()));
        Mockito.when(event.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        Mockito.when(entity.getMetadata()).thenReturn(Optional.empty());
        final MetadataFromEvent underTest = MetadataFromEvent.of(event, entity);

        assertThat(underTest.get()).isNull();
    }

    @Test
    public void entityMetadataButEventDittoHeadersHaveNoMetadata() {
        final Metadata existingMetadata = Metadata.newBuilder().set("/scruplusFine", JsonValue.of("^6,00.32")).build();
        Mockito.when(event.getEntity(Mockito.any())).thenReturn(Optional.of(thingWithoutMetadata.toJson()));
        Mockito.when(event.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        Mockito.when(entity.getMetadata()).thenReturn(Optional.of(existingMetadata));
        final MetadataFromEvent underTest = MetadataFromEvent.of(event, entity);

        assertThat(underTest.get()).isEqualTo(existingMetadata);
    }

    @Test
    public void createMetadataFromScratch() {
        final Feature modifiedFeature = fluxCapacitor.toBuilder()
                .properties(fluxCapacitorProperties.toBuilder()
                        .set("grumbo", 2)
                        .build())
                .definition(FeatureDefinition.fromIdentifier("foo:bar:1"))
                .build();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putHeader(MetadataHeaderKey.PREFIX + "/scruplusFine", "\"^6,00.32\"")
                .putHeader(MetadataHeaderKey.PREFIX + "/properties/grumbo/froodNoops", "5")
                .putHeader(MetadataHeaderKey.PREFIX + "/*/lastSeen", "1955")
                .build();
        final FeatureModified featureModified = FeatureModified.of(thingWithoutMetadata.getEntityId().orElseThrow(),
                modifiedFeature,
                3,
                dittoHeaders);
        final Metadata expected = Metadata.newBuilder()
                .set(JsonPointer.of("features/flux-capacitor/scruplusFine"), "^6,00.32")
                .set(JsonPointer.of("features/flux-capacitor/definition/lastSeen"), 1955)
                .set(JsonPointer.of("features/flux-capacitor/properties/capacity/value/lastSeen"), 1955)
                .set(JsonPointer.of("features/flux-capacitor/properties/capacity/unit/lastSeen"), 1955)
                .set(JsonPointer.of("features/flux-capacitor/properties/grumbo/froodNoops"), 5)
                .set(JsonPointer.of("features/flux-capacitor/properties/grumbo/lastSeen"), 1955)
                .build();

        final MetadataFromEvent underTest = MetadataFromEvent.of(featureModified, thingWithoutMetadata);

        assertThat(underTest.get()).isEqualTo(expected);
    }

    @Test
    public void modifyExistingMetadata() {
        final Metadata existingMetadata = Metadata.newBuilder()
                .set("floobLength", "normal")
                .set(JsonPointer.of("/features/flux-capacitor/airplaneMode"), "forbidden")
                .set(JsonPointer.of("/features/flux-capacitor/definition/lastSeen"), 2023)
                .build();
        final Thing thingWithMetadata = thingWithoutMetadata.toBuilder()
                .setMetadata(JsonPointer.empty(), existingMetadata)
                .build();
        final Feature modifiedFeature = fluxCapacitor.toBuilder()
                .properties(fluxCapacitorProperties.toBuilder()
                        .set("grumbo", 2)
                        .build())
                .definition(FeatureDefinition.fromIdentifier("foo:bar:1"))
                .build();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putHeader(MetadataHeaderKey.PREFIX + "/scruplusFine", "\"^6,00.32\"")
                .putHeader(MetadataHeaderKey.PREFIX + "/properties/grumbo/froodNoops", "5")
                .putHeader(MetadataHeaderKey.PREFIX + "/*/lastSeen", "1955")
                .build();
        final FeatureModified featureModified = FeatureModified.of(thingWithMetadata.getEntityId().orElseThrow(),
                modifiedFeature,
                4,
                dittoHeaders);
        final Metadata expected = existingMetadata.toBuilder()
                .set(JsonPointer.of("/floobLength"), "normal")
                .set(JsonPointer.of("/features/flux-capacitor/airplaneMode"), "forbidden")
                .set(JsonPointer.of("/features/flux-capacitor/scruplusFine"), "^6,00.32")
                .set(JsonPointer.of("/features/flux-capacitor/definition/lastSeen"), 1955)
                .set(JsonPointer.of("/features/flux-capacitor/properties/capacity/value/lastSeen"), 1955)
                .set(JsonPointer.of("/features/flux-capacitor/properties/capacity/unit/lastSeen"), 1955)
                .set(JsonPointer.of("/features/flux-capacitor/properties/grumbo/froodNoops"), 5)
                .set(JsonPointer.of("/features/flux-capacitor/properties/grumbo/lastSeen"), 1955)
                .build();

        final MetadataFromEvent underTest = MetadataFromEvent.of(featureModified, thingWithMetadata);

        assertThat(underTest.get()).isEqualTo(expected);
    }

    @Test
    public void keyWithSpecificPathOverwritesKeyWithWildcardPath() {
        final JsonValue metric = JsonValue.of("metric");
        final JsonValue nonMetric = JsonValue.of("non-metric");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putHeader(MetadataHeaderKey.PREFIX + "/properties/capacity/unit/type", nonMetric.toString())
                .putHeader(MetadataHeaderKey.PREFIX + "/*/type", metric.toString())
                .build();
        final Feature modifiedFeature = fluxCapacitor.toBuilder()
                .properties(fluxCapacitorProperties.toBuilder()
                        .set("grumbo", 2)
                        .build())
                .definition(FeatureDefinition.fromIdentifier("foo:bar:1"))
                .build();
        final FeatureModified featureModified = FeatureModified.of(thingWithoutMetadata.getEntityId().orElseThrow(),
                modifiedFeature,
                5,
                dittoHeaders);
        final Metadata expected = Metadata.newBuilder()
                .set(JsonPointer.of("features/flux-capacitor/definition/type"), metric)
                .set(JsonPointer.of("features/flux-capacitor/properties/capacity/value/type"), metric)
                .set(JsonPointer.of("features/flux-capacitor/properties/capacity/unit/type"), nonMetric)
                .set(JsonPointer.of("features/flux-capacitor/properties/grumbo/type"), metric)
                .build();

        final MetadataFromEvent underTest = MetadataFromEvent.of(featureModified, thingWithoutMetadata);

        assertThat(underTest.get()).isEqualTo(expected);
    }

}
