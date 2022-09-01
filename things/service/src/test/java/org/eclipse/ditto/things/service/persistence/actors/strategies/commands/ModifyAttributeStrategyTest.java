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
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.events.AttributeCreated;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ModifyAttributeStrategy}.
 */
public final class ModifyAttributeStrategyTest extends AbstractCommandStrategyTest {

    private static JsonPointer attributePointer;
    private static JsonValue attributeValue;

    private ModifyAttributeStrategy underTest;

    @BeforeClass
    public static void initTestFixture() {
        attributePointer = JsonFactory.newPointer("/foo/bar");
        attributeValue = JsonFactory.newValue("baz");
    }

    @Before
    public void setUp() {
        underTest = new ModifyAttributeStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyAttributeStrategy.class, areImmutable());
    }

    @Test
    public void modifyAttributeOfThingWithoutAttributes() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyAttribute command =
                ModifyAttribute.of(context.getState(), attributePointer, attributeValue, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2.removeAttributes(), command,
                AttributeCreated.class,
                ETagTestUtils.modifyAttributeResponse(context.getState(), attributePointer, attributeValue,
                        command.getDittoHeaders(), true));
    }

    @Test
    public void modifyAttributeOfThingWithoutThatAttribute() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyAttribute command =
                ModifyAttribute.of(context.getState(), attributePointer, attributeValue, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                AttributeCreated.class,
                ETagTestUtils.modifyAttributeResponse(context.getState(), attributePointer, attributeValue,
                        command.getDittoHeaders(), true));
    }

    @Test
    public void modifyExistingAttribute() {
        final JsonPointer existingAttributePointer = JsonFactory.newPointer("/location/latitude");
        final JsonValue newAttributeValue = JsonFactory.newValue(42.0D);

        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyAttribute command =
                ModifyAttribute.of(context.getState(), existingAttributePointer, newAttributeValue,
                        DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                AttributeModified.class,
                ETagTestUtils.modifyAttributeResponse(context.getState(), existingAttributePointer, newAttributeValue,
                        command.getDittoHeaders(), false));
    }

}
