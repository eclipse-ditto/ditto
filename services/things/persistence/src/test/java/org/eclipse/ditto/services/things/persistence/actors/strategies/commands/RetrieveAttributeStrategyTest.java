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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveAttributeStrategy}.
 */
public final class RetrieveAttributeStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveAttributeStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveAttributeStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveAttributeStrategy.class, areImmutable());
    }

    @Test
    public void retrieveExistingAttribute() {
        final CommandStrategy.Context context = getDefaultContext();
        final JsonPointer attributePointer = JsonFactory.newPointer("location/latitude");
        final RetrieveAttribute command =
                RetrieveAttribute.of(context.getThingId(), attributePointer, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveAttributeResponse.of(command.getThingId(), command.getAttributePointer(),
                        JsonFactory.newValue(44.673856), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void retrieveAttributeFromThingWithoutAttributes() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveAttribute command =
                RetrieveAttribute.of(context.getThingId(), JsonFactory.newPointer("location/latitude"),
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeAttributes(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.attributesNotFound(command.getThingId(), command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void retrieveNonExistingAttribute() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveAttribute command =
                RetrieveAttribute.of(context.getThingId(), JsonFactory.newPointer("location/bar"),
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.attributeNotFound(command.getThingId(), command.getAttributePointer(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
