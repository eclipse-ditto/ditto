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
import static org.eclipse.ditto.model.things.TestConstants.Thing.POLICY_ID;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyIdResponse;
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
    public void modifyPolicyIdOnThingWithoutPolicies() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyPolicyId command = ModifyPolicyId.of(context.getThingId(), POLICY_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, TestConstants.Thing.THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(PolicyIdCreated.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyPolicyIdResponse.created(context.getThingId(), command.getPolicyId(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyExistingPolicyId() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyPolicyId command = ModifyPolicyId.of(context.getThingId(), POLICY_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, TestConstants.Thing.THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(PolicyIdModified.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyPolicyIdResponse.modified(context.getThingId(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
