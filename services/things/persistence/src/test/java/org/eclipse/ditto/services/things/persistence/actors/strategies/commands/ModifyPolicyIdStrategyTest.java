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
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.modifyPolicyIdResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyId;
import org.eclipse.ditto.signals.events.things.PolicyIdCreated;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ModifyPolicyIdStrategy}.
 */
public final class ModifyPolicyIdStrategyTest extends AbstractCommandStrategyTest {

    private ModifyPolicyIdStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ModifyPolicyIdStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyIdStrategy.class, areImmutable());
    }

    @Test
    public void modifyPolicyIdOnThingWithoutPolicyId() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyPolicyId command = ModifyPolicyId.of(context.getThingEntityId(), POLICY_ID, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V1, command,
                PolicyIdCreated.class,
                modifyPolicyIdResponse(context.getThingEntityId(), command.getPolicyId(), command.getDittoHeaders(),
                        true));
    }

    @Test
    public void modifyExistingPolicyId() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyPolicyId command = ModifyPolicyId.of(context.getThingEntityId(), POLICY_ID, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                PolicyIdModified.class,
                modifyPolicyIdResponse(context.getThingEntityId(), command.getPolicyId(), command.getDittoHeaders(),
                        false));
    }

}
