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
import static org.eclipse.ditto.model.things.TestConstants.Authorization.AUTH_SUBJECT_GRIMES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveAclEntryStrategy}.
 */
public final class RetrieveAclEntryStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveAclEntryStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveAclEntryStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveAclEntryStrategy.class, areImmutable());
    }

    @Test
    public void retrieveAclEntryFromThingWithoutAcl() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveAclEntry command =
                RetrieveAclEntry.of(context.getThingId(), AUTH_SUBJECT_GRIMES, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.aclEntryNotFound(command.getThingId(), command.getAuthorizationSubject(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void retrieveAclEntryFromThingWithoutThatAclEntry() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveAclEntry command =
                RetrieveAclEntry.of(context.getThingId(), AUTH_SUBJECT_GRIMES, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeAllPermissionsOf(AUTH_SUBJECT_GRIMES), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.aclEntryNotFound(command.getThingId(), command.getAuthorizationSubject(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void retrieveExistingAclEntry() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveAclEntry command =
                RetrieveAclEntry.of(context.getThingId(), AUTH_SUBJECT_GRIMES, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, TestConstants.Thing.THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveAclEntryResponse.of(command.getThingId(), TestConstants.Authorization.ACL_ENTRY_GRIMES,
                        command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
