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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
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
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteAttribute command = DeleteAttribute.of(context.getState(), attrPointer, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                AttributeDeleted.class,
                DeleteAttributeResponse.of(context.getState(),
                        attrPointer, command.getDittoHeaders()));
    }

    @Test
    public void deleteAttributeFromThingWithoutAttributes() {
        final JsonPointer attrPointer = JsonFactory.newPointer("/location/longitude");
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteAttribute command = DeleteAttribute.of(context.getState(), attrPointer, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributeNotFound(context.getState(), attrPointer, command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeAttributes(), command, expectedException);
    }

    @Test
    public void deleteAttributeFromThingWithoutThatAttribute() {
        final JsonPointer attrPointer = JsonFactory.newPointer("/location/longitude");
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteAttribute command = DeleteAttribute.of(context.getState(), attrPointer, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributeNotFound(context.getState(), attrPointer, command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeAttribute(attrPointer), command, expectedException);
    }

}
