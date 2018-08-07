/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.junit.Test;

/**
 * Unit test for {@link AclEntryDeletedStrategy}.
 */
public final class AclEntryDeletedStrategyTest extends AbstractStrategyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(AclEntryDeletedStrategy.class, areImmutable());
    }

    @Test
    public void appliesEventCorrectly() {
        final AclEntryDeletedStrategy strategy = new AclEntryDeletedStrategy();
        final AclEntryDeleted event =
                AclEntryDeleted.of(THING_ID, AUTHORIZATION_SUBJECT, REVISION, DittoHeaders.empty());

        final Thing thingWithAclEntry = THING.toBuilder().setPermissions(ACL_ENTRY).build();
        final Thing thingWithEventApplied = strategy.handle(event, thingWithAclEntry, NEXT_REVISION);

        final Thing expected = THING.toBuilder()
                .setPermissions(AccessControlList.newBuilder().build())
                .setRevision(NEXT_REVISION)
                .build();
        assertThat(thingWithEventApplied).isEqualTo(expected);
    }

}