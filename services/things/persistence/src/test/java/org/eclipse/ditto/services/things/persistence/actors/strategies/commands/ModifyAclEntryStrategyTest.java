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

import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.modifyAclEntryResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Optional;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ModifyAclEntryStrategy}.
 */
public final class ModifyAclEntryStrategyTest extends AbstractCommandStrategyTest {

    private static AclEntry aclEntry;

    private ModifyAclEntryStrategy underTest;

    @BeforeClass
    public static void initTestFixture() {
        aclEntry = TestConstants.Authorization.ACL_ENTRY_OLDMAN;
    }

    @Before
    public void setUp() {
        underTest = new ModifyAclEntryStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyAclEntryStrategy.class, areImmutable());
    }

    @Test
    public void createAclEntryAsThingHasNoAclYet() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyAclEntry command = ModifyAclEntry.of(context.getThingId(), aclEntry, DittoHeaders.empty());

        final Thing thing = THING_V2.toBuilder().removePolicyId().build();

        assertModificationResult(underTest, thing, command,
                AclEntryCreated.class,
                modifyAclEntryResponse(context.getThingId(), aclEntry, command.getDittoHeaders(), true));
    }

    @Test
    public void createInvalidAclForThingWithoutAcl() {
        final CommandStrategy.Context context = getDefaultContext();
        final AclEntry modifiedAclEntry =
                ThingsModelFactory.newAclEntry(aclEntry.getAuthorizationSubject(), Permission.WRITE);
        final ModifyAclEntry command = ModifyAclEntry.of(context.getThingId(), modifiedAclEntry, DittoHeaders.empty());

        final DittoRuntimeException expectedException = ExceptionFactory.aclInvalid(context.getThingId(), Optional.of(
                MessageFormat.format("The Authorization Subject <{0}> must have at least the permission(s): <{1}>!",
                        modifiedAclEntry.getAuthorizationSubject(), Arrays.toString(Permission.values()))),
                command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2, command, expectedException);
    }

    @Test
    public void modifyExistingAclEntry() {
        final AclEntry aclEntryGrimes = TestConstants.Authorization.ACL_ENTRY_GRIMES;
        final AclEntry modifiedAclEntry =
                ThingsModelFactory.newAclEntry(aclEntryGrimes.getAuthorizationSubject(), Permission.WRITE);
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyAclEntry command = ModifyAclEntry.of(context.getThingId(), modifiedAclEntry, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V1, command,
                AclEntryModified.class,
                modifyAclEntryResponse(context.getThingId(), modifiedAclEntry, command.getDittoHeaders(), false));
    }

    @Test
    public void modifyExistingAclEntryToProduceInvalidAcl() {
        final AclEntry modifiedAclEntry =
                ThingsModelFactory.newAclEntry(aclEntry.getAuthorizationSubject(), Permission.READ);
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyAclEntry command = ModifyAclEntry.of(context.getThingId(), modifiedAclEntry, DittoHeaders.empty());

        final DittoRuntimeException expectedException = ExceptionFactory.aclInvalid(context.getThingId(), Optional.of(
                MessageFormat.format(
                        "It must contain at least one Authorization Subject with the following permission(s): <{0}>!",
                        Arrays.toString(Permission.values()))),
                command.getDittoHeaders());

        assertErrorResult(underTest, THING_V1, command, expectedException);
    }

}
