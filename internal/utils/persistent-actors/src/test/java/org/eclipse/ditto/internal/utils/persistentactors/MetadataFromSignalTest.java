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
package org.eclipse.ditto.internal.utils.persistentactors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;

import org.eclipse.ditto.base.model.entity.Entity;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link MetadataFromSignal}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MetadataFromSignalTest {

    private static FeatureProperties fluxCapacitorProperties;
    private static Feature fluxCapacitor;
    private static Thing thingWithoutMetadata;

    @Mock private ThingModifyCommand<?> command;
    @Mock private Entity<?> entity;

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
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.LATEST)
                .build();
        Mockito.when(command.getDittoHeaders()).thenReturn(dittoHeaders);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(MetadataFromSignal.class,
                areImmutable(),
                provided(Signal.class, WithOptionalEntity.class, Thing.class, Metadata.class).areAlsoImmutable());
    }

    @Test
    public void tryToGetInstanceWithNullCommand() {
        assertThatNullPointerException()
                .isThrownBy(() -> MetadataFromSignal.of(null, null, null, entity.getMetadata().orElse(null)))
                .withMessage("The signal must not be null!")
                .withNoCause();
    }

    @Test
    public void getMetadataWhenEventHasNoEntityAndEntityHasNullExistingMetadata() {
        Mockito.when(command.getEntity(Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(entity.getMetadata()).thenReturn(Optional.empty());
        final MetadataFromSignal underTest =
                MetadataFromSignal.of(command, command, null, entity.getMetadata().orElse(null));

        assertThat(underTest.get()).isNull();
    }

    @Test
    public void entityHasNoMetadataAndEventDittoHeadersHaveNoMetadata() {
        Mockito.when(command.getEntity(Mockito.any())).thenReturn(Optional.of(thingWithoutMetadata.toJson()));
        Mockito.when(command.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        Mockito.when(entity.getMetadata()).thenReturn(Optional.empty());
        final MetadataFromSignal underTest =
                MetadataFromSignal.of(command, command, null, entity.getMetadata().orElse(null));

        assertThat(underTest.get()).isNull();
    }

    @Test
    public void entityMetadataButEventDittoHeadersHaveNoMetadata() {
        final Metadata existingMetadata = Metadata.newBuilder().set("/scruplusFine", JsonValue.of("^6,00.32")).build();
        Mockito.when(command.getEntity(Mockito.any())).thenReturn(Optional.of(thingWithoutMetadata.toJson()));
        Mockito.when(command.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        Mockito.when(entity.getMetadata()).thenReturn(Optional.of(existingMetadata));
        final MetadataFromSignal underTest =
                MetadataFromSignal.of(command, command, null, entity.getMetadata().orElse(null));

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
                .putMetadata(MetadataHeaderKey.parse("/scruplusFine"), JsonValue.of("^6,00.32"))
                .putMetadata(MetadataHeaderKey.parse("/properties/grumbo/froodNoops"), JsonValue.of(5))
                .putMetadata(MetadataHeaderKey.parse("/*/lastSeen"), JsonValue.of(1955))
                .build();
        final ModifyFeature modifyFeature = ModifyFeature.of(thingWithoutMetadata.getEntityId().orElseThrow(),
                modifiedFeature,
                dittoHeaders);
        final Metadata expected = Metadata.newBuilder()
                .set(JsonPointer.of("scruplusFine"), "^6,00.32")
                .set(JsonPointer.of("definition/lastSeen"), 1955)
                .set(JsonPointer.of("properties/capacity/value/lastSeen"), 1955)
                .set(JsonPointer.of("properties/capacity/unit/lastSeen"), 1955)
                .set(JsonPointer.of("properties/grumbo/froodNoops"), 5)
                .set(JsonPointer.of("properties/grumbo/lastSeen"), 1955)
                .build();

        final MetadataFromSignal underTest = MetadataFromSignal.of(modifyFeature, modifyFeature, null, null);

        assertThat(underTest.get()).isEqualTo(expected);
    }

    @Test
    public void modifyExistingMetadata() {
        final Metadata existingMetadata = Metadata.newBuilder()
                .set(JsonPointer.of("airplaneMode"), "forbidden")
                .set(JsonPointer.of("definition/lastSeen"), 2023)
                .build();
        final Feature modifiedFeature = fluxCapacitor.toBuilder()
                .properties(fluxCapacitorProperties.toBuilder()
                        .set("grumbo", 2)
                        .build())
                .definition(FeatureDefinition.fromIdentifier("foo:bar:1"))
                .build();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putMetadata(MetadataHeaderKey.parse("/scruplusFine"), JsonValue.of("^6,00.32"))
                .putMetadata(MetadataHeaderKey.parse("/properties/grumbo/froodNoops"), JsonValue.of(5))
                .putMetadata(MetadataHeaderKey.parse("/*/lastSeen"), JsonValue.of(1955))
                .build();
        final ModifyFeature modifyFeature = ModifyFeature.of(thingWithoutMetadata.getEntityId().orElseThrow(),
                modifiedFeature,
                dittoHeaders);
        final Metadata expected = existingMetadata.toBuilder()
                .set(JsonPointer.of("airplaneMode"), "forbidden")
                .set(JsonPointer.of("scruplusFine"), "^6,00.32")
                .set(JsonPointer.of("definition/lastSeen"), 1955)
                .set(JsonPointer.of("properties/capacity/value/lastSeen"), 1955)
                .set(JsonPointer.of("properties/capacity/unit/lastSeen"), 1955)
                .set(JsonPointer.of("properties/grumbo/froodNoops"), 5)
                .set(JsonPointer.of("properties/grumbo/lastSeen"), 1955)
                .build();

        final MetadataFromSignal underTest =
                MetadataFromSignal.of(modifyFeature, modifyFeature, null, existingMetadata);

        assertThat(underTest.get()).isEqualTo(expected);
    }

    @Test
    public void keyWithSpecificPathOverwritesKeyWithWildcardPath() {
        final JsonValue metric = JsonValue.of("metric");
        final JsonValue nonMetric = JsonValue.of("non-metric");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putMetadata(MetadataHeaderKey.parse("/properties/capacity/unit/type"), nonMetric)
                .putMetadata(MetadataHeaderKey.parse("/*/type"), metric)
                .build();
        final Feature modifiedFeature = fluxCapacitor.toBuilder()
                .properties(fluxCapacitorProperties.toBuilder()
                        .set("grumbo", 2)
                        .build())
                .definition(FeatureDefinition.fromIdentifier("foo:bar:1"))
                .build();
        final ModifyFeature modifyFeature = ModifyFeature.of(thingWithoutMetadata.getEntityId().orElseThrow(),
                modifiedFeature,
                dittoHeaders);
        final Metadata expected = Metadata.newBuilder()
                .set(JsonPointer.of("definition/type"), metric)
                .set(JsonPointer.of("properties/capacity/value/type"), metric)
                .set(JsonPointer.of("properties/capacity/unit/type"), nonMetric)
                .set(JsonPointer.of("properties/grumbo/type"), metric)
                .build();

        final MetadataFromSignal underTest = MetadataFromSignal.of(modifyFeature, modifyFeature, null,
                thingWithoutMetadata.getMetadata().orElse(null));

        assertThat(underTest.get()).isEqualTo(expected);
    }

    @Test
    public void ensureThatLeafsCanOnlyBeObjects() {
        final Feature modifiedFeature = fluxCapacitor.toBuilder()
                .properties(fluxCapacitorProperties.toBuilder()
                        .set("capacitorNr", 99)
                        .build())
                .definition(FeatureDefinition.fromIdentifier("foo:bar:1"))
                .build();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putMetadata(MetadataHeaderKey.parse("/scruplusFine"), JsonValue.of("^6,00.32"))
                .putMetadata(MetadataHeaderKey.parse("/properties/capacitorNr"), JsonValue.of("unlimited"))
                .putMetadata(MetadataHeaderKey.parse("/*/lastSeen"), JsonValue.of(1955))
                .build();
        final ModifyFeature modifyFeature = ModifyFeature.of(thingWithoutMetadata.getEntityId().orElseThrow(),
                modifiedFeature,
                dittoHeaders);

        final MetadataFromSignal underTest =
                MetadataFromSignal.of(modifyFeature, modifyFeature, null, null);

        assertThat(underTest.get())
                .isNotEmpty()
                .doesNotContain(JsonField.newInstance("/properties/capacitorNr", JsonValue.of("unlimited")));
    }

    @Test
    public void createMetadataForPatchCommandWithEmptyBody() {
        final Feature emptyFeature = Feature.newBuilder().withId(fluxCapacitor.getId()).build();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putMetadata(MetadataHeaderKey.parse("/scruplusFine"), JsonValue.of("^6,00.32"))
                .putMetadata(MetadataHeaderKey.parse("/*/lastSeen"), JsonValue.of(1955))
                .build();
        final MergeThing mergeFeature = MergeThing.withFeature(thingWithoutMetadata.getEntityId().orElseThrow(),
                emptyFeature,
                dittoHeaders);
        final Metadata expected = Metadata.newBuilder()
                .set(JsonPointer.of("scruplusFine"), "^6,00.32")
                .set(JsonPointer.of("properties/capacity/value/lastSeen"), 1955)
                .set(JsonPointer.of("properties/capacity/unit/lastSeen"), 1955)
                .build();

        final MetadataFromSignal underTest =
                MetadataFromSignal.of(mergeFeature, mergeFeature, thingWithoutMetadata, null);

        assertThat(underTest.get()).isEqualTo(expected);
    }

    @Test
    public void createMetadataForPatchCommandWithEmptyBodyAndExistingMetadata() {
        final Metadata existingMetadata = Metadata.newBuilder()
                .set(JsonPointer.of("thingId"), JsonObject.newBuilder()
                        .set("description", "The Id of the thing")
                        .build())
                .set(JsonPointer.of("policyId"), JsonObject.newBuilder()
                        .set("description", "The policyId of the thing")
                        .build())
                .set(JsonPointer.of("features/flux-capacitor/properties/capacity/value/lastSeen"), 1955)
                .set(JsonPointer.of("features/flux-capacitor/properties/capacity/unit/lastSeen"), 1955)
                .build();
        final MergeThing mergeThing = MergeThing.withThing(thingWithoutMetadata.getEntityId().orElseThrow(),
                Thing.newBuilder().build(),
                DittoHeaders.empty());

        final MetadataFromSignal underTest = MetadataFromSignal.of(mergeThing, mergeThing, null, existingMetadata);

        assertThat(underTest.get()).isEqualTo(existingMetadata);
    }

}
