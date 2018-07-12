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
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit test for {@link RetrieveFeatureStrategy}.
 */
public final class RetrieveFeatureStrategyTest {

    private static final long NEXT_REVISION = 42L;

    private static DiagnosticLoggingAdapter logger;
    private static ThingSnapshotter thingSnapshotter;

    private RetrieveFeatureStrategy underTest;

    @BeforeClass
    public static void initTestConstants() {
        logger = Mockito.mock(DiagnosticLoggingAdapter.class);
        thingSnapshotter = Mockito.mock(ThingSnapshotter.class);
    }

    @Before
    public void setUp() {
        underTest = new RetrieveFeatureStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureStrategy.class, areImmutable());
    }

    @Test
    public void retrieveExistingFeature() {
        final CommandStrategy.Context context = getDefaultContext(THING_V2);
        final RetrieveFeature command = RetrieveFeature.of(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveFeatureResponse.of(THING_ID, FLUX_CAPACITOR_ID, FLUX_CAPACITOR.toJson(), DittoHeaders.empty()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void retrieveFeatureFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext(THING_V2.removeFeatures());
        final RetrieveFeature command = RetrieveFeature.of(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(ExceptionFactory.featureNotFound(THING_ID, FLUX_CAPACITOR_ID,
                DittoHeaders.empty()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void retrieveNonExistingFeature() {
        final CommandStrategy.Context context = getDefaultContext(THING_V2.removeFeature(FLUX_CAPACITOR_ID));
        final RetrieveFeature command = RetrieveFeature.of(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    private static CommandStrategy.Context getDefaultContext(final Thing thing) {
        return DefaultContext.getInstance(THING_ID, thing, NEXT_REVISION, logger, thingSnapshotter);
    }

}