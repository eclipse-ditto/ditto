/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link ImmutableFeaturePlaceholder}.
 */
public final class ImmutableFeaturePlaceholderTest {

    private static final String FEATURE_ID = "FluxCapacitor";
    private static final FeaturePlaceholder UNDER_TEST = ImmutableFeaturePlaceholder.INSTANCE;
    private static final List<String> FEATURE_IDS = List.of("bar", "baz", "foo");
    private static final List<Feature> FEATURE_LIST = FEATURE_IDS.stream()
            .map(featureId -> Feature.newBuilder().withId(featureId).build())
            .toList();
    private static final Features FEATURES = Features.newBuilder()
            .setAll(FEATURE_LIST)
            .build();
    private static final ThingId THING_ID = ThingId.of("namespace", "name");
    private static final Thing THING = Thing.newBuilder()
            .setId(THING_ID)
            .setPolicyId(PolicyId.of(THING_ID))
            .setFeatures(FEATURES)
            .build();
    private static Signal<?> signalWithFeatureId;
    private static Signal<?> signalWithOutFeatureId;

    @BeforeClass
    public static void setupClass() {
        signalWithFeatureId = mock(Signal.class, Mockito.withSettings().extraInterfaces(WithFeatureId.class));
        signalWithOutFeatureId = mock(Signal.class);
        when(((WithFeatureId) signalWithFeatureId).getFeatureId()).thenReturn(FEATURE_ID);
    }

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableFeaturePlaceholder.class, MutabilityMatchers.areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableFeaturePlaceholder.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testReplaceFeatureId() {
        assertThat(UNDER_TEST.resolveValues(signalWithFeatureId, "id")).contains(FEATURE_ID);
    }

    @Test
    public void testReplaceFeatureIdsOfModifyThing() {
        final ModifyThing modifyThing = ModifyThing.of(THING_ID, THING, null, DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(modifyThing, "id")).isEqualTo(FEATURE_IDS);
    }

    @Test
    public void testReplaceFeatureIdsOfModifyThingWithoutFeatures() {
        final Thing thingWithoutFeatures = THING.setFeatures(null);
        final ModifyThing modifyThing = ModifyThing.of(THING_ID, thingWithoutFeatures, null, DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(modifyThing, "id")).isEmpty();
    }

    @Test
    public void testReplaceFeatureIdsOfModifyAttributes() {
        final ModifyAttributes modifyAttributes =
                ModifyAttributes.of(THING_ID, Attributes.newBuilder().build(), DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(modifyAttributes, "id")).isEmpty();
    }

    @Test
    public void testReplaceFeatureIdsOfModifyFeatures() {
        final ModifyFeatures modifyFeatures = ModifyFeatures.of(THING_ID, FEATURES, DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(modifyFeatures, "id")).isEqualTo(FEATURE_IDS);
    }

    @Test
    public void testReplaceFeatureIdsOfDeleteFeatures() {
        final DeleteFeatures deleteFeatures = DeleteFeatures.of(THING_ID, DittoHeaders.empty());
        // DeleteFeatures doesn't know which features exist. So we can't resolve their IDs.
        assertThat(UNDER_TEST.resolveValues(deleteFeatures, "id")).isEmpty();
    }

    @Test
    public void testReplaceFeatureIdsOfDeleteFeature() {
        final DeleteFeature deleteFeature = DeleteFeature.of(THING_ID, "foo", DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(deleteFeature, "id")).containsExactly("foo");
    }

    @Test
    public void testReplaceFeatureIdsForMergeThingOnTopLevel() {
        final MergeThing mergeThing = MergeThing.withThing(THING_ID, THING, DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(mergeThing, "id")).isEqualTo(FEATURE_IDS);
    }

    @Test
    public void testReplaceFeatureIdsForMergeThingOnTopLevelWithNonObjectValue() {
        final MergeThing mergeThing = MergeThing.fromJson(JsonObject.newBuilder()
                .set(ThingCommand.JsonFields.TYPE, MergeThing.TYPE)
                .set(ThingCommand.JsonFields.JSON_THING_ID, THING_ID.toString())
                .set("path", "/")
                .set("value", "This is wrong.")
                .build(), DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(mergeThing, "id")).isEmpty();
    }

    @Test
    public void testReplaceFeatureIdsForMergeThingOnAttributesLevel() {
        final MergeThing mergeThing = MergeThing.withAttributes(THING_ID,
                Attributes.newBuilder()
                        .set("attributeA", 1)
                        .set("attributeB", 2)
                        .build(),
                DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(mergeThing, "id")).isEmpty();
    }

    @Test
    public void testReplaceFeatureIdsForMergeThingOnFeatureLevel() {
        final MergeThing mergeThing =
                MergeThing.withFeature(THING_ID, Feature.newBuilder().withId("foo").build(), DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(mergeThing, "id")).containsExactly("foo");
    }

    @Test
    public void testReplaceFeatureIdsForMergeThingOnFeaturePropertiesLevel() {
        final MergeThing mergeThing =
                MergeThing.withFeatureProperties(THING_ID, "foo", FeatureProperties.newBuilder().build(),
                        DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(mergeThing, "id")).containsExactly("foo");
    }

    @Test
    public void testReplaceFeatureIdsForMergeThingOnFeaturesLevel() {
        final MergeThing mergeThing = MergeThing.withFeatures(THING_ID, FEATURES, DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(mergeThing, "id")).isEqualTo(FEATURE_IDS);
    }

    @Test
    public void testReplaceFeatureIdsForMergeThingOnFeaturesLevelWithNonObjectValue() {
        final MergeThing mergeThing = MergeThing.fromJson(JsonObject.newBuilder()
                .set(ThingCommand.JsonFields.TYPE, MergeThing.TYPE)
                .set(ThingCommand.JsonFields.JSON_THING_ID, THING_ID.toString())
                .set("path", "/features")
                .set("value", "This is wrong.")
                .build(), DittoHeaders.empty());
        assertThat(UNDER_TEST.resolveValues(mergeThing, "id")).isEmpty();
    }

    @Test
    public void testReplaceFeatureIdsForMergeThingOnFeaturesLevelWithInvalidFeatures() {
        final MergeThing mergeThing = MergeThing.fromJson(JsonObject.newBuilder()
                .set(ThingCommand.JsonFields.TYPE, MergeThing.TYPE)
                .set(ThingCommand.JsonFields.JSON_THING_ID, THING_ID.toString())
                .set("path", "/features")
                .set("value", JsonObject.newBuilder().set("wrong", "features").build())
                .build(), DittoHeaders.empty());
        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> UNDER_TEST.resolveValues(mergeThing, "id"));
    }

    @Test
    public void testUnknownPlaceholderReturnsEmpty() {
        assertThat(UNDER_TEST.resolveValues(signalWithFeatureId, "feature_id")).isEmpty();
    }

    @Test
    public void testSignalWhichIsNotWithFeatureIdReturnsEmpty() {
        assertThat(UNDER_TEST.resolveValues(signalWithOutFeatureId, "id")).isEmpty();
    }

}
