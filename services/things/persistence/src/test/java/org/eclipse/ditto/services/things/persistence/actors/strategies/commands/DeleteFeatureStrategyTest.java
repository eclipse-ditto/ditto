/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureResponse;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link DeleteFeatureStrategy}.
 */
public final class DeleteFeatureStrategyTest extends AbstractCommandStrategyTest {

    private static String featureId;

    private DeleteFeatureStrategy underTest;

    @BeforeClass
    public static void initTestFixture() {
        featureId = TestConstants.Feature.FLUX_CAPACITOR_ID;
    }

    @Before
    public void setUp() {
        underTest = new DeleteFeatureStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteFeatureStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteFeatureFromThing() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeature command = DeleteFeature.of(context.getThingId(), featureId, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeatureDeleted.class);
        assertThat(result.getCommandResponse()).contains(DeleteFeatureResponse.of(context.getThingId(),
                command.getFeatureId(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeatureFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeature command = DeleteFeature.of(context.getThingId(), featureId, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeatureFromThingWithoutThatFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeature command = DeleteFeature.of(context.getThingId(), "myFeature", DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
