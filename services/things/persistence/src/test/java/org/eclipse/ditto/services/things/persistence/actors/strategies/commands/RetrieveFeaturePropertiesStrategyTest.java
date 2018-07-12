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
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit test for {@link RetrieveFeaturePropertiesStrategy}.
 */
public final class RetrieveFeaturePropertiesStrategyTest {

    private static final long NEXT_REVISION = 42L;

    private static DiagnosticLoggingAdapter logger;
    private static ThingSnapshotter thingSnapshotter;

    private RetrieveFeaturePropertiesStrategy underTest;

    @BeforeClass
    public static void initTestConstants() {
        logger = Mockito.mock(DiagnosticLoggingAdapter.class);
        thingSnapshotter = Mockito.mock(ThingSnapshotter.class);
    }

    @Before
    public void setUp() {
        underTest = new RetrieveFeaturePropertiesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeaturePropertiesStrategy.class, areImmutable());
    }

    @Test
    public void getProperties() {
        final CommandStrategy.Context context = getDefaultContext(THING_V2);
        final RetrieveFeatureProperties command =
                RetrieveFeatureProperties.of(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveFeaturePropertiesResponse.of(THING_ID, FLUX_CAPACITOR_ID, FLUX_CAPACITOR_PROPERTIES,
                        DittoHeaders.empty()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getPropertiesFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext(THING_V2.removeFeatures());
        final RetrieveFeatureProperties command =
                RetrieveFeatureProperties.of(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getNonExistingProperties() {
        final CommandStrategy.Context context =
                getDefaultContext(THING_V2.setFeature(FLUX_CAPACITOR.removeProperties()));
        final RetrieveFeatureProperties command =
                RetrieveFeatureProperties.of(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featurePropertiesNotFound(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    private static CommandStrategy.Context getDefaultContext(final Thing thing) {
        return DefaultContext.getInstance(THING_ID, thing, NEXT_REVISION, logger, thingSnapshotter);
    }

}