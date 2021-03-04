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
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.modifyAttributesResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesModified;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ModifyAttributesStrategy}.
 */
public final class ModifyAttributesStrategyTest extends AbstractCommandStrategyTest {

    private static Attributes modifiedAttributes;

    private ModifyAttributesStrategy underTest;

    @BeforeClass
    public static void initTestFixture() {
        modifiedAttributes = TestConstants.Thing.ATTRIBUTES.toBuilder()
                .set("maker", "ACME")
                .build();
    }

    @Before
    public void setUp() {
        underTest = new ModifyAttributesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyAttributesStrategy.class, areImmutable());
    }

    @Test
    public void modifyAttributesOfThingWithoutAttributes() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyAttributes command =
                ModifyAttributes.of(context.getState(), modifiedAttributes, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2.removeAttributes(), command,
                AttributesCreated.class,
                modifyAttributesResponse(context.getState(), modifiedAttributes, command.getDittoHeaders(), true));
    }

    @Test
    public void modifyAttributesOfThingWithAttributes() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyAttributes command =
                ModifyAttributes.of(context.getState(), modifiedAttributes, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                AttributesModified.class,
                modifyAttributesResponse(context.getState(), modifiedAttributes, command.getDittoHeaders(), false));
    }

}
