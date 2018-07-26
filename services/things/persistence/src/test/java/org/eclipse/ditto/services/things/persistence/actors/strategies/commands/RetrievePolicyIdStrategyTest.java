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
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
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
        final RetrievePolicyId command = RetrievePolicyId.of(THING_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrievePolicyIdResponse.of(command.getThingId(), POLICY_ID, DittoHeaders.empty()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void retrieveNonExistingPolicyId() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrievePolicyId command = RetrievePolicyId.of(THING_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.toBuilder().removePolicyId().build(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(PolicyIdNotAccessibleException.newBuilder(command.getThingId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
