/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.akka.controlflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link IdPartitioner}.
 */
public final class IdPartitionerTest {

    private static final String SPECIAL_ENFORCEMENT_LANE_HEADER_KEY = "ditto-internal-special-enforcement-lane";
    private static final int PARALLELISM = 10;

    private IdPartitioner<Object> underTest;

    @Before
    public void setUp() {
        underTest = IdPartitioner.of(SPECIAL_ENFORCEMENT_LANE_HEADER_KEY, PARALLELISM);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(IdPartitioner.class, areImmutable());
    }

    @Test
    public void tryToGetInstanceWithNullHeaderKey() {
        assertThatNullPointerException()
                .isThrownBy(() -> IdPartitioner.of(null, 1))
                .withMessage("The specialEnforcementLaneHeaderKey must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithEmptyHeaderKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IdPartitioner.of("", 1))
                .withMessage("The argument 'specialEnforcementLaneHeaderKey' must not be empty!")
                .withNoCause();
    }

    @Test
    public void specialLaneEvaluatesToZero() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putHeader(SPECIAL_ENFORCEMENT_LANE_HEADER_KEY, "")
                .build();
        final WithDittoHeaders<?> message = Mockito.mock(WithDittoHeaders.class);
        Mockito.when(message.getDittoHeaders()).thenReturn(dittoHeaders);

        assertThat(underTest.apply(message)).isZero();
    }

    @Test
    public void messageWithoutDittoHeadersAndWithoutIdEvaluatesToZero() {
        assertThat(underTest.apply(new Object())).isZero();
    }

    @Test
    public void messageWithDummyIdEvaluatesToExpected() {
        final EntityId entityId = Mockito.mock(EntityId.class);
        Mockito.when(entityId.isDummy()).thenReturn(true);
        final WithId message = Mockito.mock(WithId.class);
        Mockito.when(message.getEntityId()).thenReturn(entityId);
        final int expected = Math.abs(message.hashCode() % PARALLELISM) + 1;

        assertThat(underTest.apply(message)).isEqualTo(expected);
    }

    @Test
    public void messageWithRealIdEvaluatesToExpected() {
        final EntityId entityId = Mockito.mock(EntityId.class);
        Mockito.when(entityId.isDummy()).thenReturn(false);
        final WithId message = Mockito.mock(WithId.class);
        Mockito.when(message.getEntityId()).thenReturn(entityId);
        final int expected = Math.abs(entityId.hashCode() % PARALLELISM) + 1;

        assertThat(underTest.apply(message)).isEqualTo(expected);
    }

    @Test
    public void twoDifferentMessagesWithSameThingIdEvaluateToSamePartitionNumber() {
        final ThingId thingId = ThingId.generateRandom();
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeaders);
        final ModifyFeatureProperty modifyFeatureProperty =
                ModifyFeatureProperty.of(thingId, "myFeature", JsonPointer.of("some/property"), JsonValue.of(false),
                        dittoHeaders);

        final Integer partitionNumberForRetrieveThing = underTest.apply(retrieveThing);
        final Integer partitionNumberForModifyFeatureProperty = underTest.apply(modifyFeatureProperty);

        assertThat(partitionNumberForRetrieveThing).isEqualTo(partitionNumberForModifyFeatureProperty);
    }

}