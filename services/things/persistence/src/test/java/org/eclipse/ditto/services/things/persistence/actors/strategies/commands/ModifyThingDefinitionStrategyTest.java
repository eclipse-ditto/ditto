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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.eclipse.ditto.model.things.TestConstants.Thing.DEFINITION;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.modifyThingDefinitionResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingDefinition;
import org.eclipse.ditto.signals.events.things.ThingDefinitionCreated;
import org.eclipse.ditto.signals.events.things.ThingDefinitionModified;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ModifyPolicyIdStrategy}.
 */
public final class ModifyThingDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private ModifyThingDefinitionStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ModifyThingDefinitionStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyThingDefinitionStrategy.class, areImmutable());
    }

    @Test
    public void modifyDefinitionOnThingWithoutDefinition() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyThingDefinition command = ModifyThingDefinition.of(context.getState(), DEFINITION,
                DittoHeaders.empty());

        assertModificationResult(underTest, THING_V1, command, ThingDefinitionCreated.class,
                modifyThingDefinitionResponse(context.getState(), command.getDefinition(),
                        command.getDittoHeaders(), true));
    }

    @Test
    public void modifyExistingDefinition() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyThingDefinition command = ModifyThingDefinition.of(context.getState(), DEFINITION,
                DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                ThingDefinitionModified.class, modifyThingDefinitionResponse(context.getState(),
                        command.getDefinition(),
                        command.getDittoHeaders(), false));
    }

}