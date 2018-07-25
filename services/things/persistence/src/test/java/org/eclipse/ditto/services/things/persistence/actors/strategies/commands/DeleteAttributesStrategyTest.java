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
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteAttributesStrategy}.
 */
public final class DeleteAttributesStrategyTest extends AbstractCommandStrategyTest {

    private DeleteAttributesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteAttributesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteAttributesStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteAllAttributesFromThing() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteAttributes command = DeleteAttributes.of(context.getThingId(), DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(AttributesDeleted.class);
        assertThat(result.getCommandResponse()).contains(
                DeleteAttributesResponse.of(context.getThingId(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();

    }

    @Test
    public void deleteAttributesFromThingWithoutAttributes() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteAttributes command = DeleteAttributes.of(context.getThingId(), DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeAttributes(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.attributesNotFound(context.getThingId(), command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
