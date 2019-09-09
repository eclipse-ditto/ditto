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

import static org.eclipse.ditto.model.things.TestConstants.Authorization.AUTH_SUBJECT_GRIMES;
import static org.eclipse.ditto.model.things.TestConstants.Authorization.AUTH_SUBJECT_OLDMAN;
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
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
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
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final AuthorizationSubject authSubject = AUTH_SUBJECT_GRIMES;
        final DeleteAclEntry command = DeleteAclEntry.of(context.getEntityId(), authSubject, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.aclEntryNotFound(context.getEntityId(), authSubject, command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2, command, expectedException);
    }

    @Test
    public void deleteLastAclEntryWithMinRequiredPermissions() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteAclEntry command =
                DeleteAclEntry.of(context.getEntityId(), AUTH_SUBJECT_GRIMES, DittoHeaders.empty());
        final DittoRuntimeException expectedException = ExceptionFactory.aclInvalid(context.getEntityId(), Optional.of(
                MessageFormat.format(
                        "It must contain at least one Authorization Subject with the following permission(s): <{0}>!",
                        Arrays.toString(Permission.values()))),
                command.getDittoHeaders());

        assertErrorResult(underTest, THING_V1.removeAllPermissionsOf(AUTH_SUBJECT_OLDMAN), command, expectedException);
    }

    @Test
    public void successfullyDeleteAclEntry() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final AuthorizationSubject authSubject = AUTH_SUBJECT_GRIMES;
        final DeleteAclEntry command = DeleteAclEntry.of(context.getEntityId(), authSubject, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V1, command,
                AclEntryDeleted.class,
                DeleteAclEntryResponse.of(context.getEntityId(), authSubject, command.getDittoHeaders()));
    }

}
