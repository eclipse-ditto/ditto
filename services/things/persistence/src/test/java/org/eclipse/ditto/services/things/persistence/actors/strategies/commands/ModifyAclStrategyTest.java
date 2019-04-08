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

import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.modifyAclResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ModifyAclStrategy}.
 */
public final class ModifyAclStrategyTest extends AbstractCommandStrategyTest {

    private ModifyAclStrategy underTest;

    @Before
    public void setUp() {
        underTest = new ModifyAclStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyAclStrategy.class, areImmutable());
    }

    @Test
    public void modifyExistingAclEntryToProduceInvalidAcl() {
        final CommandStrategy.Context context = getDefaultContext();
        final AccessControlList acl = ThingsModelFactory.newAcl(TestConstants.Authorization.ACL_ENTRY_OLDMAN);
        final ModifyAcl modifyAcl = ModifyAcl.of(context.getThingId(), acl, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V1, modifyAcl,
                AclModified.class,
                modifyAclResponse(context.getThingId(), acl, modifyAcl.getDittoHeaders(), false));
    }
}
