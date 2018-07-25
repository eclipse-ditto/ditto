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
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
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

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featuresNotFound(context.getThingId(), command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeaturesFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatures command = DeleteFeatures.of(context.getThingId(), DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(ThingModifiedEvent.class);
        assertThat(result.getCommandResponse()).contains(
                DeleteFeaturesResponse.of(context.getThingId(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
