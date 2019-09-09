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

import static org.eclipse.ditto.model.things.TestConstants.Thing.POLICY_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.retrievePolicyIdResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
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
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrievePolicyId command = RetrievePolicyId.of(context.getEntityId(), DittoHeaders.empty());
        final RetrievePolicyIdResponse expectedResponse =
                retrievePolicyIdResponse(command.getThingEntityId(), POLICY_ID, DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveNonExistingPolicyId() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrievePolicyId command = RetrievePolicyId.of(context.getEntityId(), DittoHeaders.empty());
        final PolicyIdNotAccessibleException expectedException =
                PolicyIdNotAccessibleException.newBuilder(command.getThingEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();

        assertErrorResult(underTest, THING_V2.toBuilder().removePolicyId().build(), command, expectedException);
    }

}
