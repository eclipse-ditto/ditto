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
import static org.eclipse.ditto.model.things.TestConstants.Feature.FEATURES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit test for {@link RetrieveFeaturesStrategy}.
 */
public final class RetrieveFeaturesStrategyTest {

    private static final long NEXT_REVISION = 42L;

    private static DiagnosticLoggingAdapter logger;
    private static ThingSnapshotter thingSnapshotter;

    private RetrieveFeaturesStrategy underTest;

    @BeforeClass
    public static void initTestConstants() {
        logger = Mockito.mock(DiagnosticLoggingAdapter.class);
        thingSnapshotter = Mockito.mock(ThingSnapshotter.class);
    }

    @Before
    public void setUp() {
        underTest = new RetrieveFeaturesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeaturesStrategy.class, areImmutable());
    }

    @Test
    public void retrieveFeaturesWithoutSelectedFields() {
        final CommandStrategy.Context context = getDefaultContext(THING_V2);
        final RetrieveFeatures command = RetrieveFeatures.of(THING_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveFeaturesResponse.of(THING_ID, FEATURES.toJson(command.getImplementedSchemaVersion()),
                        DittoHeaders.empty()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void retrieveFeaturesWithSelectedFields() {
        final CommandStrategy.Context context = getDefaultContext(THING_V2);
        final JsonFieldSelector selectedFields = JsonFactory.newFieldSelector("maker");
        final RetrieveFeatures command = RetrieveFeatures.of(THING_ID, selectedFields, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveFeaturesResponse.of(THING_ID,
                        FEATURES.toJson(command.getImplementedSchemaVersion(), selectedFields), DittoHeaders.empty()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void retrieveFeaturesFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext(THING_V2.removeFeatures());
        final RetrieveFeatures command = RetrieveFeatures.of(THING_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(ExceptionFactory.featuresNotFound(THING_ID, DittoHeaders.empty()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    private static CommandStrategy.Context getDefaultContext(final Thing thing) {
        return DefaultContext.getInstance(THING_ID, thing, NEXT_REVISION, logger, thingSnapshotter);
    }

}