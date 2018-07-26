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
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeModified;
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
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyAttribute command =
                ModifyAttribute.of(context.getThingId(), attributePointer, attributeValue, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeAttributes(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(AttributeCreated.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyAttributeResponse.created(context.getThingId(), attributePointer, attributeValue,
                        command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyAttributeOfThingWithoutThatAttribute() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyAttribute command =
                ModifyAttribute.of(context.getThingId(), attributePointer, attributeValue, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(AttributeCreated.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyAttributeResponse.created(context.getThingId(), attributePointer, attributeValue,
                        command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyExistingAttribute() {
        final JsonPointer existingAttributePointer = JsonFactory.newPointer("/location/latitude");
        final JsonValue newAttributeValue = JsonFactory.newValue(42.0D);

        final CommandStrategy.Context context = getDefaultContext();
        final ModifyAttribute command =
                ModifyAttribute.of(context.getThingId(), existingAttributePointer, newAttributeValue,
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(AttributeModified.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyAttributeResponse.modified(context.getThingId(), existingAttributePointer,
                        command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
