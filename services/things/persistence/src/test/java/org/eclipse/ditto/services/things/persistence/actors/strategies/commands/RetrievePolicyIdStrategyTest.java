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

import static org.eclipse.ditto.model.things.TestConstants.Thing.POLICY_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.retrievePolicyIdResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyId;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyIdResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrievePolicyIdStrategy}.
 */
public final class RetrievePolicyIdStrategyTest extends AbstractCommandStrategyTest {

    private RetrievePolicyIdStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrievePolicyIdStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrievePolicyIdStrategy.class, areImmutable());
    }

    @Test
    public void retrieveExistingPolicyId() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrievePolicyId command = RetrievePolicyId.of(context.getThingId(), DittoHeaders.empty());
        final RetrievePolicyIdResponse expectedResponse =
                retrievePolicyIdResponse(command.getThingId(), POLICY_ID, DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveNonExistingPolicyId() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrievePolicyId command = RetrievePolicyId.of(context.getThingId(), DittoHeaders.empty());
        final PolicyIdNotAccessibleException expectedException = PolicyIdNotAccessibleException.newBuilder(command.getThingId())
                .dittoHeaders(command.getDittoHeaders())
                .build();

        assertErrorResult(underTest, THING_V2.toBuilder().removePolicyId().build(), command, expectedException);
    }

}
