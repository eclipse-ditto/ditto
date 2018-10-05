/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
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

        assertModificationResult(underTest, THING_V2, command,
                FeatureDeleted.class,
                DeleteFeatureResponse.of(context.getThingId(), command.getFeatureId(), command.getDittoHeaders()));
    }

    @Test
    public void deleteFeatureFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeature command = DeleteFeature.of(context.getThingId(), featureId, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void deleteFeatureFromThingWithoutThatFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeature command = DeleteFeature.of(context.getThingId(), "myFeature", DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2, command, expectedException);
    }

}
