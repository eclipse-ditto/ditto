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
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttribute;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributeResponse;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
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
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonPointer attributePointer = JsonFactory.newPointer("location/latitude");
        final RetrieveAttribute command =
                RetrieveAttribute.of(context.getState(), attributePointer, DittoHeaders.empty());
        final RetrieveAttributeResponse expectedResponse =
                ETagTestUtils.retrieveAttributeResponse(command.getEntityId(), command.getAttributePointer(),
                        JsonFactory.newValue(44.673856), command.getDittoHeaders());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveAttributeFromThingWithoutAttributes() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveAttribute command =
                RetrieveAttribute.of(context.getState(), JsonFactory.newPointer("location/latitude"),
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributesNotFound(command.getEntityId(), command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeAttributes(), command, expectedException);
    }

    @Test
    public void retrieveNonExistingAttribute() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveAttribute command =
                RetrieveAttribute.of(context.getState(), JsonFactory.newPointer("location/bar"),
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributeNotFound(command.getEntityId(), command.getAttributePointer(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2, command, expectedException);
    }

}
