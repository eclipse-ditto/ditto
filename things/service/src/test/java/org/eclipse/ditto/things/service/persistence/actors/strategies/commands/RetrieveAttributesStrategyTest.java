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

import static org.eclipse.ditto.things.model.TestConstants.Thing.ATTRIBUTES;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributesResponse;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveAttributesStrategy}.
 */
public final class RetrieveAttributesStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveAttributesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveAttributesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveAttributesStrategy.class, areImmutable());
    }

    @Test
    public void retrieveAttributesWithoutSelectedFields() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveAttributes command = RetrieveAttributes.of(context.getState(), DittoHeaders.empty());
        final RetrieveAttributesResponse expectedResponse =
                ETagTestUtils.retrieveAttributesResponse(context.getState(), ATTRIBUTES,
                        ATTRIBUTES.toJson(command.getImplementedSchemaVersion()), DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveAttributesWithSelectedFields() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonFieldSelector selectedFields = JsonFactory.newFieldSelector("maker");
        final RetrieveAttributes command =
                RetrieveAttributes.of(context.getState(), selectedFields, DittoHeaders.empty());
        final RetrieveAttributesResponse expectedResponse =
                ETagTestUtils.retrieveAttributesResponse(context.getState(), ATTRIBUTES,
                        ATTRIBUTES.toJson(command.getImplementedSchemaVersion(), selectedFields), DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveAttributesFromThingWithoutAttributes() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveAttributes command = RetrieveAttributes.of(context.getState(), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributesNotFound(context.getState(), DittoHeaders.empty());

        assertErrorResult(underTest, THING_V2.removeAttributes(), command, expectedException);
    }

}
