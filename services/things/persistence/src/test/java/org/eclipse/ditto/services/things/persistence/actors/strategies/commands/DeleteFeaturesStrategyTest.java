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

import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteFeaturesStrategy}.
 */
public final class DeleteFeaturesStrategyTest extends AbstractCommandStrategyTest {

    private DeleteFeaturesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteFeaturesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteFeaturesStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteFeaturesFromThing() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatures command = DeleteFeatures.of(context.getThingId(), DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeaturesDeleted.class,
                DeleteFeaturesResponse.of(context.getThingId(), command.getDittoHeaders()));
    }

    @Test
    public void deleteFeaturesFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatures command = DeleteFeatures.of(context.getThingId(), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featuresNotFound(context.getThingId(), command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

}
