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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.events.AttributesCreated;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
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
                ETagTestUtils.modifyAttributeResponse(context.getState(), modifiedAttributes, command.getDittoHeaders(), true));
    }

    @Test
    public void modifyAttributesOfThingWithAttributes() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyAttributes command =
                ModifyAttributes.of(context.getState(), modifiedAttributes, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                AttributesModified.class,
                ETagTestUtils.modifyAttributeResponse(context.getState(), modifiedAttributes, command.getDittoHeaders(), false));
    }

}
