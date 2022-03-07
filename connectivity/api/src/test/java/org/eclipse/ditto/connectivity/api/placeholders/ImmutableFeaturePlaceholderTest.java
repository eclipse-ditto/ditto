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
import static org.mockito.Mockito.when;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
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
    private static Signal<?> signalWithFeatureId;
    private static Signal<?> signalWithOutFeatureId;

    @BeforeClass
    public static void setupClass() {
        signalWithFeatureId = Mockito.mock(Signal.class, Mockito.withSettings().extraInterfaces(WithFeatureId.class));
        signalWithOutFeatureId = Mockito.mock(Signal.class);
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
    public void testUnknownPlaceholderReturnsEmpty() {
        assertThat(UNDER_TEST.resolveValues(signalWithFeatureId, "feature_id")).isEmpty();
    }

    @Test
    public void testSignalWhichIsNotWithFeatureIdReturnsEmpty() {
        assertThat(UNDER_TEST.resolveValues(signalWithOutFeatureId, "id")).isEmpty();
    }

}
