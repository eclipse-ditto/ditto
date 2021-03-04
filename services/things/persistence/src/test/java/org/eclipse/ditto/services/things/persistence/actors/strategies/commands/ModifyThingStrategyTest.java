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

import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.modifyThingResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ModifyThingStrategy}.
 */
public final class ModifyThingStrategyTest extends AbstractCommandStrategyTest {

    private static final Instant MODIFIED = Instant.ofEpochSecond(123456789);
    private ModifyThingStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ModifyThingStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyThingStrategy.class, areImmutable());
    }

    @Test
    public void modifyExisting() {

        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ThingId thingId = context.getState();

        final Thing existing = THING_V2.toBuilder().setRevision(NEXT_REVISION - 1).build();

        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setRevision(NEXT_REVISION)
                .setModified(MODIFIED)
                .setCreated(MODIFIED)
                .build();

        final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, DittoHeaders.empty());

        assertModificationResult(underTest, existing, modifyThing,
                ThingModified.class, modifyThingResponse(existing, thing, modifyThing.getDittoHeaders(), false));
    }

}