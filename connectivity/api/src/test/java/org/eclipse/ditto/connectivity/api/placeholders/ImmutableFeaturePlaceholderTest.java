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
package org.eclipse.ditto.connectivity.api.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
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
        final ModifyThing modifyThing = mock(ModifyThing.class);
        final Thing thing = mock(Thing.class);
        when(thing.getFeatures()).thenReturn(Optional.of(FEATURES));
        when(modifyThing.getThing()).thenReturn(thing);
        assertThat(UNDER_TEST.resolveValues(modifyThing, "id")).isEqualTo(FEATURE_IDS);
    }

    @Test
    public void testReplaceFeatureIdsOfModifyThingWithouFeatures() {
        final ModifyThing modifyThing = mock(ModifyThing.class);
        final Thing thing = mock(Thing.class);
        when(thing.getFeatures()).thenReturn(Optional.empty());
        when(modifyThing.getThing()).thenReturn(thing);
        assertThat(UNDER_TEST.resolveValues(modifyThing, "id")).isEmpty();
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
