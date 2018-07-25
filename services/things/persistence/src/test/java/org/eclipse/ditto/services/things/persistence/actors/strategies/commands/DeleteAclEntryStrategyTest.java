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
import static org.eclipse.ditto.model.things.TestConstants.Authorization.AUTH_SUBJECT_OLDMAN;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Optional;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntryResponse;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteAclEntryStrategy}.
 */
public final class DeleteAclEntryStrategyTest extends AbstractCommandStrategyTest {

    private DeleteAclEntryStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteAclEntryStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteAclEntryStrategy.class, areImmutable());
    }

    @Test
    public void applyStrategyOnThingWithoutAcl() {
        final CommandStrategy.Context context = getDefaultContext();
        final AuthorizationSubject authSubject = AUTH_SUBJECT_GRIMES;
        final DeleteAclEntry command = DeleteAclEntry.of(context.getThingId(), authSubject, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.aclEntryNotFound(THING_ID, authSubject, command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteLastAclEntryWithMinRequiredPermissions() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteAclEntry command = DeleteAclEntry.of(context.getThingId(), AUTH_SUBJECT_GRIMES, DittoHeaders.empty());
        final DittoRuntimeException expectedException = ExceptionFactory.aclInvalid(THING_ID, Optional.of(
                MessageFormat.format(
                        "It must contain at least one Authorization Subject with the following permission(s): <{0}>!",
                        Arrays.toString(Permission.values()))),
                command.getDittoHeaders());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeAllPermissionsOf(AUTH_SUBJECT_OLDMAN), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(expectedException);
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void successfullyDeleteAclEntry() {
        final CommandStrategy.Context context = getDefaultContext();
        final AuthorizationSubject authSubject = AUTH_SUBJECT_GRIMES;
        final DeleteAclEntry command = DeleteAclEntry.of(context.getThingId(), authSubject, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(AclEntryDeleted.class);
        assertThat(result.getCommandResponse()).contains(
                DeleteAclEntryResponse.of(context.getThingId(), authSubject, command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
