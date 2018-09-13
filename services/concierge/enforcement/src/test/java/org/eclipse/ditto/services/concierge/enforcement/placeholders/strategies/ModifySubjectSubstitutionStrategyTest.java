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
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;
import org.junit.Test;

/**
 * Tests {@link ModifySubjectSubstitutionStrategy} in context of
 * {@link org.eclipse.ditto.services.concierge.enforcement.placeholders.PlaceholderSubstitution}.
 */
public class ModifySubjectSubstitutionStrategyTest extends AbstractSubstitutionStrategyTestBase {

    private static final String POLICY_ID = "org.eclipse.ditto:my-policy";
    private static final String LABEL = "my-label";

    @Override
    public void assertImmutability() {
        assertInstancesOf(ModifySubjectSubstitutionStrategy.class, areImmutable());
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoPlaceholderIsSpecified() {
        final ModifySubject commandWithoutPlaceholders = ModifySubject.of(POLICY_ID,
                Label.of(LABEL), Subject.newInstance("my-issuer:my-id", SubjectType.GENERATED),
                DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        assertThat(response).isSameAs(commandWithoutPlaceholders);
    }

    @Test
    public void applyReturnsTheReplacedCommandInstanceWhenPlaceholderIsSpecified() {
        final ModifySubject commandWithoutPlaceholders = ModifySubject.of(POLICY_ID,
                Label.of(LABEL), Subject.newInstance("{{ request:subjectId }}", SubjectType.GENERATED),
                DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        final ModifySubject expectedCommandWithPlaceholders = ModifySubject.of(commandWithoutPlaceholders.getId(),
                commandWithoutPlaceholders.getLabel(), Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED),
                DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandWithPlaceholders);
    }

}
