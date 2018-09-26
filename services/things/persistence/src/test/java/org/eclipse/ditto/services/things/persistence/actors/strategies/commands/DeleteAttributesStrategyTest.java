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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteAttributesStrategy}.
 */
public final class DeleteAttributesStrategyTest extends AbstractCommandStrategyTest {

    private DeleteAttributesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteAttributesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteAttributesStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteAllAttributesFromThing() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteAttributes command = DeleteAttributes.of(context.getThingId(), DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                AttributesDeleted.class,
                DeleteAttributesResponse.of(context.getThingId(), command.getDittoHeaders()));
    }

    @Test
    public void deleteAttributesFromThingWithoutAttributes() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteAttributes command = DeleteAttributes.of(context.getThingId(), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributesNotFound(context.getThingId(), command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeAttributes(), command, expectedException);
    }

}
