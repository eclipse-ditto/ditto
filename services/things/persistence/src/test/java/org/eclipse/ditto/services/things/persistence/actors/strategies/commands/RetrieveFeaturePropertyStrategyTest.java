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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit test for {@link RetrieveFeaturePropertyStrategy}.
 */
public final class RetrieveFeaturePropertyStrategyTest {

    private static final long NEXT_REVISION = 42L;

    private static DiagnosticLoggingAdapter logger;
    private static ThingSnapshotter thingSnapshotter;

    private RetrieveFeaturePropertyStrategy underTest;

    @BeforeClass
    public static void initTestConstants() {
        logger = Mockito.mock(DiagnosticLoggingAdapter.class);
        thingSnapshotter = Mockito.mock(ThingSnapshotter.class);
    }

    @Before
    public void setUp() {
        underTest = new RetrieveFeaturePropertyStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeaturePropertyStrategy.class, areImmutable());
    }

    @Test
    public void getProperty() {
        final CommandStrategy.Context context = getDefaultContext(THING_V2);
        final JsonPointer propertyPointer = JsonFactory.newPointer("target_year_1");
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(THING_ID, FLUX_CAPACITOR_ID, propertyPointer, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveFeaturePropertyResponse.of(THING_ID, FLUX_CAPACITOR_ID, propertyPointer,
                        JsonFactory.newValue(1955), DittoHeaders.empty()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getPropertyFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext(THING_V2.removeFeatures());
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(THING_ID, FLUX_CAPACITOR_ID, JsonFactory.newPointer("target_year_1"),
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getPropertyFromFeatureWithoutProperties() {
        final CommandStrategy.Context context =
                getDefaultContext(THING_V2.setFeature(FLUX_CAPACITOR.removeProperties()));
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(THING_ID, FLUX_CAPACITOR_ID, JsonFactory.newPointer("target_year_1"),
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featurePropertiesNotFound(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getNonExistentProperty() {
        final JsonPointer propertyPointer = JsonFactory.newPointer("target_year_1");
        final CommandStrategy.Context context =
                getDefaultContext(THING_V2.setFeature(FLUX_CAPACITOR.removeProperty(propertyPointer)));
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(THING_ID, FLUX_CAPACITOR_ID, propertyPointer, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featurePropertyNotFound(THING_ID, FLUX_CAPACITOR_ID, propertyPointer,
                        DittoHeaders.empty()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    private static CommandStrategy.Context getDefaultContext(final Thing thing) {
        return DefaultContext.getInstance(THING_ID, thing, NEXT_REVISION, logger, thingSnapshotter);
    }

}