/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
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
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyAttributes command =
                ModifyAttributes.of(context.getThingId(), modifiedAttributes, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2.removeAttributes(), command,
                AttributesCreated.class,
                modifyAttributesResponse(context.getThingId(), modifiedAttributes, command.getDittoHeaders(), true));
    }

    @Test
    public void modifyAttributesOfThingWithAttributes() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyAttributes command =
                ModifyAttributes.of(context.getThingId(), modifiedAttributes, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                AttributesModified.class,
                modifyAttributesResponse(context.getThingId(), modifiedAttributes, command.getDittoHeaders(), false));
    }

}
