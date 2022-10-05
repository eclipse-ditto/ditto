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

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributeResponse;
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
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

    /**
     * Having attributes:
     * <code>
     * "attributes": {
     *     "complex": {
     *         "nested": "foo"
     *     }
     * }
     * </code>
     * a delete on <code>/complex/nested/non/existent/path</code> should result in an error
     */
    @Test
    public void deleteFromComplexNestedAttributeWithoutThatPath() {
        // the last known object key must be still part of the pointer, or the test will not work. Since "nested"
        // is the last known part of the pointer that is part of the attributes, it has to be a primitive value.
        // if it was an object and the object did not contain "non", then the test would fail trivially.
        final JsonPointer attrPointer = JsonFactory.newPointer("/complex/nested/non/existent/path");
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteAttribute command = DeleteAttribute.of(context.getState(), attrPointer, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributeNotFound(context.getState(), attrPointer, command.getDittoHeaders());

        final Thing thing = THING_V2.toBuilder()
                .removeAllAttributes()
                .setAttribute(JsonPointer.of("/complex"), JsonObject.of("{\"nested\": \"foo\"}"))
                .build();

        assertErrorResult(underTest, thing, command, expectedException);
    }

    /**
     * Having attributes:
     * <code>
     * "attributes": {
     *     "flat":"foobar"
     * }
     * </code>
     * a delete on <code>/flat/non/existent/path</code> should result in an error
     */
    @Test
    public void deleteFromFlatAttributeWithoutThatPath() {

        final JsonPointer attrPointer = JsonFactory.newPointer("/flat/non/existent/path");
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteAttribute command = DeleteAttribute.of(context.getState(), attrPointer, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributeNotFound(context.getState(), attrPointer, command.getDittoHeaders());

        final Thing thing = THING_V2.toBuilder()
                .removeAllAttributes()
                .setAttribute(JsonPointer.of("/flat"), JsonValue.of("foobar"))
                .build();

        assertErrorResult(underTest, thing, command, expectedException);
    }

}
