/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
        final DeleteFeatures command = DeleteFeatures.of(context.getThingEntityId(), DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeaturesDeleted.class,
                DeleteFeaturesResponse.of(context.getThingEntityId(), command.getDittoHeaders()));
    }

    @Test
    public void deleteFeaturesFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatures command = DeleteFeatures.of(context.getThingEntityId(), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featuresNotFound(context.getThingEntityId(), command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

}
