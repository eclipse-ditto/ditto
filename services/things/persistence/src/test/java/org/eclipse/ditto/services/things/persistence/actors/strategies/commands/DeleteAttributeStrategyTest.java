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
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
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

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(AttributeDeleted.class);
        assertThat(result.getCommandResponse()).contains(DeleteAttributeResponse.of(context.getThingId(),
                attrPointer, command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteAttributeFromThingWithoutAttributes() {
        final JsonPointer attrPointer = JsonFactory.newPointer("/location/longitude");
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteAttribute command = DeleteAttribute.of(context.getThingId(), attrPointer, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeAttributes(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.attributeNotFound(context.getThingId(), attrPointer, command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteAttributeFromThingWithoutThatAttribute() {
        final JsonPointer attrPointer = JsonFactory.newPointer("/location/longitude");
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteAttribute command = DeleteAttribute.of(context.getThingId(), attrPointer, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeAttribute(attrPointer), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.attributeNotFound(context.getThingId(), attrPointer, command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
