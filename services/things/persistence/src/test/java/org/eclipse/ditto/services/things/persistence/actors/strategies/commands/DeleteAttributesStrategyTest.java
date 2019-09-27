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
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
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
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteAttributes command = DeleteAttributes.of(context.getState(), DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                AttributesDeleted.class,
                DeleteAttributesResponse.of(context.getState(), command.getDittoHeaders()));
    }

    @Test
    public void deleteAttributesFromThingWithoutAttributes() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteAttributes command = DeleteAttributes.of(context.getState(), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributesNotFound(context.getState(), command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeAttributes(), command, expectedException);
    }

}
