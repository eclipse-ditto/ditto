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
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;
import org.junit.Test;

/**
 * Tests {@link ModifyAclEntrySubstitutionStrategy} in context of
 * {@link org.eclipse.ditto.services.concierge.enforcement.placeholders.PlaceholderSubstitution}.
 */
public class ModifyAclEntrySubstitutionStrategyTest extends AbstractSubstitutionStrategyTestBase {


    @Override
    public void assertImmutability() {
        assertInstancesOf(ModifyAclEntrySubstitutionStrategy.class, areImmutable());
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoPlaceholderIsSpecified() {
        final AclEntry aclEntryWithoutPlaceholders =
                AclEntry.newInstance(AuthorizationSubject.newInstance(SUBJECT_ID), ACL_PERMISSIONS);
        final ModifyAclEntry commandWithoutPlaceholders = ModifyAclEntry.of(THING_ID,
                aclEntryWithoutPlaceholders, DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        assertThat(response).isSameAs(commandWithoutPlaceholders);
    }

    @Test
    public void applyReturnsTheReplacedCommandInstanceWhenPlaceholderIsSpecified() {
        final AclEntry aclEntryWithPlaceholders =
                AclEntry.newInstance(AuthorizationSubject.newInstance(SUBJECT_ID_PLACEHOLDER), ACL_PERMISSIONS);
        final ModifyAclEntry commandWithPlaceholders = ModifyAclEntry.of(THING_ID,
                aclEntryWithPlaceholders, DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithPlaceholders);

        final AclEntry expectedAclEntryReplaced =
                AclEntry.newInstance(AuthorizationSubject.newInstance(SUBJECT_ID), ACL_PERMISSIONS);
        final ModifyAclEntry expectedCommandReplaced = ModifyAclEntry.of(THING_ID,
                expectedAclEntryReplaced, DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandReplaced);
    }

}
