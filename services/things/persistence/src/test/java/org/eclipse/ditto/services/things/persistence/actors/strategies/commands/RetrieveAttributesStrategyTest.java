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

import static org.eclipse.ditto.model.things.TestConstants.Thing.ATTRIBUTES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.retrieveAttributesResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
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
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveAttributes command = RetrieveAttributes.of(context.getThingId(), DittoHeaders.empty());
        final RetrieveAttributesResponse expectedResponse =
                retrieveAttributesResponse(context.getThingId(), ATTRIBUTES,
                        ATTRIBUTES.toJson(command.getImplementedSchemaVersion()), DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveAttributesWithSelectedFields() {
        final CommandStrategy.Context context = getDefaultContext();
        final JsonFieldSelector selectedFields = JsonFactory.newFieldSelector("maker");
        final RetrieveAttributes command =
                RetrieveAttributes.of(context.getThingId(), selectedFields, DittoHeaders.empty());
        final RetrieveAttributesResponse expectedResponse =
                retrieveAttributesResponse(context.getThingId(), ATTRIBUTES,
                    ATTRIBUTES.toJson(command.getImplementedSchemaVersion(), selectedFields), DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveAttributesFromThingWithoutAttributes() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveAttributes command = RetrieveAttributes.of(context.getThingId(), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.attributesNotFound(context.getThingId(), DittoHeaders.empty());

        assertErrorResult(underTest, THING_V2.removeAttributes(), command, expectedException);
    }

}
