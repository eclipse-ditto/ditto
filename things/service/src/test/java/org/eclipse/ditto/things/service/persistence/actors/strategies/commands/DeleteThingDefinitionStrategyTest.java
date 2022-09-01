/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingDefinitionNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteThingDefinitionStrategy}.
 */
public final class DeleteThingDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private DeleteThingDefinitionStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteThingDefinitionStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteThingDefinitionStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteDefinitionFromThing() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteThingDefinition command = DeleteThingDefinition.of(context.getState(), DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                ThingDefinitionDeleted.class,
                DeleteThingDefinitionResponse.of(context.getState(), command.getDittoHeaders()));
    }

    @Test
    public void deleteDefinitionFromThingWithoutDefinition() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteThingDefinition command = DeleteThingDefinition.of(context.getState(), DittoHeaders.empty());
        final ThingDefinitionNotAccessibleException expectedException =
                ThingDefinitionNotAccessibleException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();

        assertErrorResult(underTest, THING_V2.removeDefinition(), command, expectedException);
    }

}
