/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;
import org.junit.Test;

/**
 * Tests {@link ModifyAclSubstitutionStrategy} in context of
 * {@link org.eclipse.ditto.services.concierge.enforcement.placeholders.PlaceholderSubstitution}.
 */
public class ModifyAclSubstitutionStrategyTest extends AbstractSubstitutionStrategyTestBase {


    @Override
    public void assertImmutability() {
        assertInstancesOf(ModifyAclSubstitutionStrategy.class, areImmutable());
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoPlaceholderIsSpecified() {
        final AclEntry aclEntryWithoutPlaceholders =
                AclEntry.newInstance(AuthorizationSubject.newInstance(SUBJECT_ID), ACL_PERMISSIONS);
        final ModifyAcl commandWithoutPlaceholders = ModifyAcl.of(THING_ID,
                AccessControlList.newBuilder().set(aclEntryWithoutPlaceholders).build(), DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        assertThat(response).isSameAs(commandWithoutPlaceholders);
    }

    @Test
    public void applyReturnsTheReplacedCommandInstanceWhenPlaceholderIsSpecified() {
        final AclEntry aclEntryWithPlaceholders =
                AclEntry.newInstance(AuthorizationSubject.newInstance(SUBJECT_ID_PLACEHOLDER), ACL_PERMISSIONS);
        final AclEntry anotherAclEntry =
                AclEntry.newInstance(AuthorizationSubject.newInstance(SUBJECT_ID_2), ACL_PERMISSIONS);
        final AccessControlList aclWithPlaceholders = AccessControlList.newBuilder()
                .set(aclEntryWithPlaceholders)
                .set(anotherAclEntry)
                .build();
        final ModifyAcl commandWithPlaceholders = ModifyAcl.of(THING_ID,
                aclWithPlaceholders, DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithPlaceholders);

        final AclEntry expectedAclEntryReplaced =
                AclEntry.newInstance(AuthorizationSubject.newInstance(SUBJECT_ID), ACL_PERMISSIONS);
        final AccessControlList expectedAcl = AccessControlList.newBuilder()
                .set(expectedAclEntryReplaced)
                .set(anotherAclEntry)
                .build();
        final ModifyAcl expectedCommandReplaced = ModifyAcl.of(THING_ID, expectedAcl, DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandReplaced);
    }

}
