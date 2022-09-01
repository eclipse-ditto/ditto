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

import static org.eclipse.ditto.things.model.TestConstants.Thing.POLICY_ID;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyIdNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrievePolicyId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrievePolicyIdResponse;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
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
        final RetrievePolicyId command = RetrievePolicyId.of(context.getState(), DittoHeaders.empty());
        final RetrievePolicyIdResponse expectedResponse =
                ETagTestUtils.retrievePolicyIdResponse(command.getEntityId(), POLICY_ID, DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveNonExistingPolicyId() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrievePolicyId command = RetrievePolicyId.of(context.getState(), DittoHeaders.empty());
        final PolicyIdNotAccessibleException expectedException =
                PolicyIdNotAccessibleException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();

        assertErrorResult(underTest, THING_V2.toBuilder().removePolicyId().build(), command, expectedException);
    }

}
