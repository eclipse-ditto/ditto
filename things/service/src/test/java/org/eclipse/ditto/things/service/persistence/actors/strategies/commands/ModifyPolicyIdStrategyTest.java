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
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyId;
import org.eclipse.ditto.things.model.signals.events.PolicyIdModified;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
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
    public void modifyExistingPolicyId() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyPolicyId command = ModifyPolicyId.of(context.getState(), POLICY_ID, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                PolicyIdModified.class, ETagTestUtils.modifyPolicyIdResponse(context.getState(), command.getPolicyEntityId(),
                        command.getDittoHeaders(), false));
    }

}
