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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteThingStrategy}.
 */
public final class DeleteThingStrategyTest extends AbstractCommandStrategyTest {

    private DeleteThingStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteThingStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteThingStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteThing() {
         // The Thing of the context is ignored, only the Thing ID is of importance.
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteThing command = DeleteThing.of(context.getThingId(), DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, null, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(ThingDeleted.class);
        assertThat(result.getCommandResponse()).contains(
                DeleteThingResponse.of(context.getThingId(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isTrue();
    }

}
