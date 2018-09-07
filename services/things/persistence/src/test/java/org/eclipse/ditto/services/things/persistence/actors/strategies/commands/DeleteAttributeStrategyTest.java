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

import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributeResponse;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteAttributeStrategy}.
 */
public final class DeleteAttributeStrategyTest extends AbstractCommandStrategyTest {

    private DeleteAttributeStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteAttributeStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteAttributeStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteAttribute() {
        final JsonPointer attrPointer = JsonFactory.newPointer("/location/longitude");
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteAttribute command = DeleteAttribute.of(context.getThingId(), attrPointer, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                AttributeDeleted.class,
                DeleteAttributeResponse.of(context.getThingId(),
                        attrPointer, command.getDittoHeaders()));
    }

    @Test
    public void deleteAttributeFromThingWithoutAttributes() {
        final JsonPointer attrPointer = JsonFactory.newPointer("/location/longitude");
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteAttribute command = DeleteAttribute.of(context.getThingId(), attrPointer, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributeNotFound(context.getThingId(), attrPointer, command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeAttributes(), command, expectedException);
    }

    @Test
    public void deleteAttributeFromThingWithoutThatAttribute() {
        final JsonPointer attrPointer = JsonFactory.newPointer("/location/longitude");
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteAttribute command = DeleteAttribute.of(context.getThingId(), attrPointer, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributeNotFound(context.getThingId(), attrPointer, command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeAttribute(attrPointer), command, expectedException);
    }

}
